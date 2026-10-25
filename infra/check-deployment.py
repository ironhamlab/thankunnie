"""프론트 HTML·정적 자산과 백엔드 프록시 응답을 확인한다."""

import json
import sys
from html.parser import HTMLParser
from urllib.error import HTTPError
from urllib.parse import urljoin, urlparse
from urllib.request import urlopen

BASE = "http://127.0.0.1/"


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


def check():
    # React/Vite 진입 HTML 확인
    with urlopen(BASE, timeout=5) as response:
        if response.status != 200:
            raise ValueError("Frontend did not return HTTP 200")

        content_type = response.headers.get("Content-Type", "")

        if "text/html" not in content_type:
            raise ValueError("Frontend did not return HTML")

        parser = AssetParser()
        parser.feed(response.read().decode("utf-8"))

    if not parser.has_root or not parser.module_scripts:
        raise ValueError("Vite application root/module script is missing")

    # HTML에서 참조하는 JS·CSS 파일 확인
    for asset in set(parser.assets):
        target = urljoin(BASE, asset)

        if urlparse(target).netloc != urlparse(BASE).netloc:
            raise ValueError("Unexpected external application asset")

        with urlopen(target, timeout=5) as response:
            if response.status != 200 or not response.read(1):
                raise ValueError("Application asset is missing")

            content_type = response.headers.get("Content-Type", "")

            if "text/html" in content_type:
                raise ValueError("Asset request returned SPA fallback HTML")

    # 존재하지 않는 API에 대한 백엔드 공통 응답 확인
    try:
        with urlopen(urljoin(BASE, "api/infra-check"), timeout=5):
            raise ValueError("Expected backend COMMON_404 response")

    except HTTPError as error:
        if error.code != 404:
            raise ValueError("Unexpected backend status") from error

        payload = json.loads(error.read())

        if payload.get("error", {}).get("code") != "COMMON_404":
            raise ValueError("Backend error response did not match")


if __name__ == "__main__":
    try:
        check()

    except Exception as error:
        print(
            f"Deployment check failed: {error}",
            file=sys.stderr,
        )
        sys.exit(1)

    print("Frontend HTML/assets and backend routing verified")