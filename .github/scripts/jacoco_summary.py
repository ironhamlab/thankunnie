"""JaCoCo XML 리포트를 GitHub Actions Job Summary 용 마크다운 표로 바꾼다.

사용: python3 jacoco_summary.py <jacocoTestReport.xml>
전체 라인·분기 커버리지와 도메인 패키지(com/ucamp/app/domain/{도메인})별 커버리지를 출력한다.
리포트가 없으면 안내 문구만 출력하고 정상 종료한다 (CI 를 실패시키지 않음).
"""
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict

DOMAIN_PREFIX = "com/ucamp/app/domain/"


def ratio(covered, missed):
    total = covered + missed
    return f"{covered / total * 100:.1f}%" if total else "-"


def counters(element):
    result = {}
    for counter in element.findall("counter"):
        result[counter.get("type")] = (int(counter.get("covered")), int(counter.get("missed")))
    return result


def main(path):
    try:
        root = ET.parse(path).getroot()
    except (OSError, ET.ParseError):
        print("### 테스트 커버리지\n\n커버리지 리포트가 없습니다. 테스트 단계 로그를 확인해 주세요.")
        return

    total = counters(root)
    line = total.get("LINE", (0, 0))
    branch = total.get("BRANCH", (0, 0))

    domains = defaultdict(lambda: {"LINE": [0, 0], "BRANCH": [0, 0]})
    for package in root.findall("package"):
        name = package.get("name")
        if not name.startswith(DOMAIN_PREFIX):
            continue
        domain = name[len(DOMAIN_PREFIX):].split("/")[0]
        for kind, (covered, missed) in counters(package).items():
            if kind in ("LINE", "BRANCH"):
                domains[domain][kind][0] += covered
                domains[domain][kind][1] += missed

    print("### 테스트 커버리지 (JaCoCo)\n")
    print(f"- 라인: **{ratio(*line)}** ({line[0]}/{sum(line)})")
    print(f"- 분기: **{ratio(*branch)}** ({branch[0]}/{sum(branch)})")
    print("\n| 도메인 | 라인 | 분기 |")
    print("|---|---:|---:|")
    for domain in sorted(domains):
        values = domains[domain]
        print(f"| {domain} | {ratio(*values['LINE'])} | {ratio(*values['BRANCH'])} |")
    print("\n설정·DTO·Lombok 생성 코드는 제외했습니다. 상세 리포트는 Actions 아티팩트 `jacoco-report` 참고.")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "")
