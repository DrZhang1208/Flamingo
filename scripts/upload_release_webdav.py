#!/usr/bin/env python3
"""Upload a file or packaged Release using private, machine-local WebDAV credentials."""
import argparse
import base64
import hashlib
import http.client
import json
import os
from pathlib import Path
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
import xml.etree.ElementTree as ET

CONFIG = Path(os.environ.get('FLAMINGO_WEBDAV_CONFIG', str(Path.home() / '.config/flamingo/release-webdav.json')))


class NoRedirect(urllib.request.HTTPRedirectHandler):
    # Never forward Authorization to a redirected endpoint.
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class WebDav:
    def __init__(self, config):
        if urllib.parse.urlsplit(config['url']).scheme != 'https':
            raise ValueError('WebDAV requires HTTPS')
        self.base = config['url'].rstrip('/') + '/' + urllib.parse.quote(config['directory'].strip('/'), safe='/') + '/'
        self.auth = 'Basic ' + base64.b64encode((config['username'] + ':' + config['password']).encode()).decode()
        self.opener = urllib.request.build_opener(NoRedirect)

    def request(self, method, name='', data=None, headers=None):
        req = urllib.request.Request(self.base + urllib.parse.quote(name, safe=''), method=method, data=data,
            headers={'Authorization': self.auth, 'User-Agent': 'Flamingo-Release-Uploader/1.0', **(headers or {})})
        for attempt in range(4):
            try:
                return self.opener.open(req, timeout=300)
            except urllib.error.HTTPError as error:
                if error.code not in (429, 500, 502, 503, 504, 520, 521, 522, 523, 524) or attempt == 3:
                    raise
            except (OSError, http.client.HTTPException):
                if attempt == 3:
                    raise
            print(f'Transient connection failure; retrying {method} ({attempt + 1}/3)...', flush=True)
            time.sleep(attempt + 1)

    def check_directory(self):
        with self.request('PROPFIND', headers={'Depth': '0'}) as response:
            root = ET.fromstring(response.read())
            if response.status != 207 or root.find('.//{DAV:}collection') is None:
                raise ValueError('Target is not a WebDAV directory')
        print('WebDAV target directory is accessible.', flush=True)

    def check_write(self):
        name = 'Flamingo-upload-check-' + uuid.uuid4().hex + '.txt'
        payload = uuid.uuid4().bytes
        created = False
        try:
            with self.request('PUT', name, payload):
                created = True
            with self.request('GET', name, headers={'Cache-Control': 'no-cache'}) as response:
                if response.read() != payload:
                    raise ValueError('WebDAV write verification failed')
            print('WebDAV upload and download verified.', flush=True)
        finally:
            # Only remove our unique probe, never existing user files.
            if created:
                try:
                    with self.request('DELETE', name):
                        pass
                except urllib.error.HTTPError as error:
                    if error.code != 404:
                        raise

    def remote_digest(self, name):
        for attempt in range(4):
            try:
                digest = hashlib.sha256()
                size = 0
                with self.request('GET', name, headers={'Cache-Control': 'no-cache'}) as response:
                    while True:
                        chunk = response.read(1024 * 1024)
                        if not chunk:
                            break
                        size += len(chunk)
                        digest.update(chunk)
                return digest.hexdigest(), size
            except urllib.error.HTTPError:
                raise
            except (OSError, http.client.HTTPException):
                if attempt == 3:
                    raise
                print('Retrying remote checksum download...', flush=True)
        raise ValueError('Remote checksum unavailable')

    def upload(self, apk, name='Flamingo-latest-release.apk'):
        if not name or name in ('.', '..') or any(c in name for c in ('/', '\\', '\r', '\n')):
            raise ValueError('Remote name must be a single filename')
        payload = apk.read_bytes()
        expected = hashlib.sha256(payload).hexdigest()
        # A content-addressed temporary name permits safe resumption after process/network failure.
        staging = 'Flamingo-upload-' + hashlib.sha256(name.encode()).hexdigest()[:12] + '-' + expected + '.part'
        with self.request('OPTIONS') as response:
            partial = 'sabredav-partialupdate' in response.headers.get('DAV', '')
        offset = 0
        if partial:
            try:
                digest, size = self.remote_digest(staging)
                if size <= len(payload) and digest == hashlib.sha256(payload[:size]).hexdigest():
                    offset = size
                    print(f'Resuming verified partial upload at {offset * 100 // max(1, len(payload))}%.', flush=True)
                else:
                    with self.request('DELETE', staging):
                        pass
            except urllib.error.HTTPError as error:
                if error.code != 404:
                    raise
        print('Uploading to a temporary file...', flush=True)
        if partial and payload:
            while offset < len(payload):
                chunk = payload[offset:offset + 1024 * 1024]
                method = 'PUT' if offset == 0 else 'PATCH'
                headers = {'Content-Length': str(len(chunk))}
                if offset:
                    headers.update({'Content-Type': 'application/x-sabredav-partialupdate',
                        'X-Update-Range': f'bytes={offset}-{offset + len(chunk) - 1}'})
                with self.request(method, staging, chunk, headers):
                    pass
                offset += len(chunk)
                print(f'Upload confirmed: {offset * 100 // max(1, len(payload))}%', flush=True)
        else:
            with self.request('PUT', staging, payload, {'Content-Length': str(len(payload)),
                    'Content-Type': 'application/vnd.android.package-archive'}):
                pass
        print('Upload completed; verifying remote file...', flush=True)
        if self.remote_digest(staging) != (expected, len(payload)):
            with self.request('DELETE', staging):
                pass
            raise ValueError('Uploaded checksum does not match local file')
        try:
            with self.request('MOVE', staging, headers={'Destination': self.base + urllib.parse.quote(name, safe=''), 'Overwrite': 'T'}):
                pass
        except urllib.error.HTTPError as error:
            # MOVE may have succeeded before a lost response; never mistake an unrelated file for it.
            if error.code != 404 or self.remote_digest(name) != (expected, len(payload)):
                raise
        print('Release uploaded; downloaded SHA-256 matches local APK.' if name == 'Flamingo-latest-release.apk' else 'File uploaded; downloaded SHA-256 matches local file.', flush=True)
        print(self.base + urllib.parse.quote(name, safe=''), flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='Check access and upload/download/delete a unique small probe')
    parser.add_argument('--config', type=Path, default=CONFIG, help='Private JSON credential file')
    parser.add_argument('--file', type=Path, help='Upload this file instead of a Release APK')
    parser.add_argument('--remote-name', help='Destination filename (overwrites this name after checksum verification)')
    parser.add_argument('--directory', help='Override the directory from configuration')
    parser.add_argument('--metadata', type=Path, default=Path(__file__).resolve().parents[1] / 'app/build/outputs/apk/release/output-metadata.json')
    args = parser.parse_args()
    config = json.loads(args.config.expanduser().read_text())
    if args.directory is not None:
        config['directory'] = args.directory
    dav = WebDav(config)
    dav.check_directory()
    if args.check:
        dav.check_write()
    elif args.file is not None:
        file = args.file.expanduser().resolve(strict=True)
        if not file.is_file():
            raise ValueError('Upload source must be a regular file')
        dav.upload(file, args.remote_name or file.name)
    else:
        elements = json.loads(args.metadata.read_text())['elements']
        if len(elements) != 1 or elements[0].get('filters'):
            raise ValueError('Expected one universal Release APK')
        apk = args.metadata.parent / elements[0]['outputFile']
        if not apk.is_file():
            raise ValueError('Release APK is missing; build assembleRelease first')
        dav.upload(apk, args.remote_name or 'Flamingo-latest-release.apk')


if __name__ == '__main__':
    try:
        main()
    except urllib.error.HTTPError as error:
        print(f'WebDAV failed: HTTP {error.code}. Local file remains available.', file=sys.stderr)
        sys.exit(1)
    except (OSError, ValueError, KeyError, ET.ParseError, http.client.HTTPException) as error:
        # Do not print server bodies, headers, or configuration values.
        print(f'WebDAV failed ({type(error).__name__}). Local file remains available.', file=sys.stderr)
        sys.exit(1)
