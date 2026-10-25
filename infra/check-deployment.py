"""프론트 HTML·정적 자산과 백엔드 응답을 확인한다."""

import argparse
import json
import socket
import ssl
import sys
from html.parser import HTMLParser
from http.client import HTTPSConnection
from urllib.error import HTTPError
from urllib.parse import urljoin, urlparse
from urllib.request import HTTPSHandler, ProxyHandler, build_opener

FRONTEND_URL = "https://dev.thankunnie.shop/"
BACKEND_URL = "https://api-dev.thankunnie.shop/"
LEGACY_URL = "http://127.0.0.1/"


class LocalHTTPSConnection(HTTPSConnection):
    """인증서의 도메인은 검증하면서 연결 대상만 로컬로 지정한다."""

    def connect(self):
        connection = socket.create_connection(
            ("127.0.0.1", self.port),
            timeout=self.timeout,
        )

        try:
            self.sock = self._context.wrap_socket(
                connection,
                server_hostname=self.host,
            )
        except Exception:
            connection.close()
            raise


class LocalHTTPSHandler(HTTPSHandler):
    def https_open(self, request):
        return self.do_open(
            LocalHTTPSConnection,
            request,
            context=self._context,
        )


OPENER = build_opener(
    ProxyHandler({}),
    LocalHTTPSHandler(context=ssl.create_default_context()),
)


class AssetParser(HTMLParser):
    def __init__(self):
        super().__init__()
        self.assets = []
        self.module_scripts = []
        self.has_root = False

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)

        if attrs.get("id") == "root":
            self.has_root = True

        if tag == "script" and attrs.get("src"):
            self.assets.append(attrs["src"])

            if attrs.get("type") == "module":
                self.module_scripts.append(attrs["src"])

        if tag == "link" and attrs.get("rel") in (
            "stylesheet",
            "modulepreload",
        ):
            if attrs.get("href"):
                self.assets.append(attrs["href"])


def check_backend(url):
    try:
        with OPENER.open(url, timeout=5):
            raise ValueError("Expected backend COMMON_404 response")

    except HTTPError as error:
        if error.code != 404:
            raise ValueError("Unexpected backend status") from error

        payload = json.loads(error.read())

        if payload.get("error", {}).get("code") != "COMMON_404":
            raise ValueError("Backend error response did not match")


def check(frontend_url, backend_url):
    with OPENER.open(frontend_url, timeout=5) as response:
        if response.status != 200:
            raise ValueError("Frontend did not return HTTP 200")

        content_type = response.headers.get("Content-Type", "")

        if "text/html" not in content_type:
            raise ValueError("Frontend did not return HTML")

        parser = AssetParser()
        parser.feed(response.read().decode("utf-8"))

    if not parser.has_root or not parser.module_scripts:
        raise ValueError("Vite application root/module script is missing")

    for asset in set(parser.assets):
        target = urljoin(frontend_url, asset)

        if urlparse(target).netloc != urlparse(frontend_url).netloc:
            raise ValueError("Unexpected external application asset")

        with OPENER.open(target, timeout=5) as response:
            if response.status != 200 or not response.read(1):
                raise ValueError("Application asset is missing")

            content_type = response.headers.get("Content-Type", "")

            if "text/html" in content_type:
                raise ValueError("Asset request returned SPA fallback HTML")

    # 프론트의 /api 프록시와 백엔드 전용 도메인을 모두 확인한다.
    urls = {
        urljoin(frontend_url, "api/infra-check"),
        urljoin(backend_url, "api/infra-check"),
    }

    for url in urls:
        check_backend(url)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--allow-http-fallback",
        action="store_true",
        help="이전 HTTP 이미지로 롤백할 때만 사용",
    )
    args = parser.parse_args()

    try:
        check(FRONTEND_URL, BACKEND_URL)
        print("HTTPS frontend/assets and backend routing verified")
        return 0

    except Exception as error:
        print(f"HTTPS check failed: {error}", file=sys.stderr)

        if not args.allow_http_fallback:
            return 1

    try:
        check(LEGACY_URL, LEGACY_URL)
        print("Previous HTTP deployment verified")
        return 0

    except Exception as error:
        print(f"HTTP rollback check failed: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())