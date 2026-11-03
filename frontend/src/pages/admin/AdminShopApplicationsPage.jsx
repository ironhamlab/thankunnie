import { useCallback, useEffect, useRef, useState } from 'react';
import { SHOP_APPLICATION_TABS, SHOP_REVIEW_GUIDE } from '@/constants/shopApplication';
import { useAdminShopStore } from '@/store/useAdminShopStore';
import { formatDateTime } from '@/utils/reservationFormat';
import Alert from '@/components/common/Alert';
import Badge from '@/components/common/Badge';
import Button from '@/components/common/Button';
import EmptyState from '@/components/common/EmptyState';
import Panel from '@/components/common/Panel';
import ReservationPagination from '@/components/reservation/ReservationPagination';
import ShopApplicationDialog from './components/ShopApplicationDialog';
import styles from './AdminShopApplicationsPage.module.css';

// 스크롤을 줄이도록 한 번에 적게 보여주고 나머지는 페이지로 넘긴다
const PAGE_SIZE = 10;
const KEYWORD_MAX_LENGTH = 50;
const SEARCH_DEBOUNCE_MS = 200;
// 표가 카드로 바뀌는 폭. module.css 의 미디어 쿼리와 같게 둔다
const CARD_LAYOUT_QUERY = '(max-width: 640px)';
const tabOf = (status) => SHOP_APPLICATION_TABS.find((tab) => tab.status === status);

/**
 * 관리자 매장 가입 신청. 상태별 목록을 보고, 승인 대기 신청의 내용을 확인해 승인하거나 사유와 함께 거절한다.
 */
export default function AdminShopApplicationsPage() {
  const applications = useAdminShopStore((state) => state.applications);
  const pageInfo = useAdminShopStore((state) => state.pageInfo);
  const listStatus = useAdminShopStore((state) => state.listStatus);
  const listError = useAdminShopStore((state) => state.listError);
  const loadApplications = useAdminShopStore((state) => state.loadApplications);
  const [status, setStatus] = useState(SHOP_APPLICATION_TABS[0].status);
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState(null);
  const [notice, setNotice] = useState('');
  // 글자마다 요청하지 않도록 입력 중인 값과 실제로 찾은 값을 나눈다
  const [input, setInput] = useState('');
  const [keyword, setKeyword] = useState('');
  const panelRef = useRef(null);

  const reload = useCallback(
    () => loadApplications({ status, keyword: keyword || undefined, page, size: PAGE_SIZE }),
    [loadApplications, status, keyword, page],
  );

  useEffect(() => {
    reload();
  }, [reload]);

  // 입력이 잠시 멈추면 찾는다. 검색 버튼이나 Enter 는 기다리지 않고 바로 찾는다
  useEffect(() => {
    const next = input.trim();
    if (next === keyword) return undefined;
    const timer = setTimeout(() => {
      setPage(0);
      setNotice('');
      setKeyword(next);
    }, SEARCH_DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [input, keyword]);

  const changeTab = (next) => {
    setStatus(next);
    setPage(0);
    setNotice('');
    setInput('');
    setKeyword('');
  };

  // 폰은 목록이 화면보다 길어 페이지를 넘겨도 스크롤이 아래에 남으므로 목록 맨 위로 올린다
  const changePage = (next) => {
    setPage(next);
    if (window.matchMedia(CARD_LAYOUT_QUERY).matches) {
      panelRef.current?.scrollIntoView({ block: 'start' });
    }
  };

  const handleSearch = (event) => {
    event.preventDefault();
    setPage(0);
    setNotice('');
    setKeyword(input.trim());
  };

  // 처리한 신청은 지금 탭에서 빠지므로 목록을 다시 받는다. 마지막 한 건을 처리했으면 앞 페이지로 간다
  const handleDone = (message) => {
    setSelected(null);
    setNotice(message);
    if (applications.length === 1 && page > 0) {
      setPage(page - 1);
    } else {
      reload();
    }
  };

  return (
    <div className="b-stack">
      <div className="b-title-row">
        <h1>매장 가입 신청</h1>
        <p className="b-muted">{SHOP_REVIEW_GUIDE}</p>
      </div>
      <div className={`b-options b-tabs ${styles.tabs}`} role="group" aria-label="신청 상태">
        {SHOP_APPLICATION_TABS.map((tab) => (
          <button
            key={tab.status}
            type="button"
            aria-pressed={status === tab.status}
            className={status === tab.status ? 'is-selected' : ''}
            onClick={() => changeTab(tab.status)}
          >
            {tab.label}
          </button>
        ))}
      </div>
      <form className={styles.search} role="search" onSubmit={handleSearch}>
        <input
          type="search"
          aria-label="매장 검색"
          placeholder="매장명, 아이디, 연락처로 검색"
          maxLength={KEYWORD_MAX_LENGTH}
          value={input}
          onChange={(event) => setInput(event.target.value)}
        />
        <Button type="submit" variant="primary">
          검색
        </Button>
      </form>
      {notice && (
        <Alert tone="success" role="status">
          {notice}
        </Alert>
      )}
      <Panel as="section" className="b-table-panel" ref={panelRef}>
        {listStatus === 'failed' && (
          <Alert tone="error" role="alert">
            {listError.message}{' '}
            <Button size="small" onClick={reload}>
              다시 불러오기
            </Button>
          </Alert>
        )}
        <div className={styles.list}>
          {listStatus !== 'failed' && (
            <div className="b-table-scroll">
              <table className={styles.table}>
                <thead>
                  <tr>
                    {['신청일', '매장명', '업종', '연락처', '상태', '상세'].map((header) => (
                      <th key={header}>{header}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {applications.map((item) => {
                    const tab = tabOf(item.status);
                    return (
                      <tr key={item.shopId}>
                        <td className={styles.date}>{formatDateTime(item.appliedAt)}</td>
                        <td className={styles.name}>
                          <strong>{item.name}</strong>
                        </td>
                        <td className={styles.category}>{item.categoryName ?? '-'}</td>
                        <td className={styles.phone}>{item.phone}</td>
                        <td className={styles.status}>
                          <Badge tone={tab?.tone}>{tab?.label ?? item.status}</Badge>
                        </td>
                        <td className={styles.detail}>
                          <Button size="small" type="button" onClick={() => setSelected(item)}>
                            상세 보기
                          </Button>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
          {listStatus === 'succeeded' && !applications.length && (
            <EmptyState as="p">
              {keyword ? '검색 결과가 없습니다.' : `${tabOf(status).label} 신청이 없습니다.`}
            </EmptyState>
          )}
          {listStatus === 'loading' && !applications.length && (
            <EmptyState as="p" role="status">
              신청을 불러오는 중입니다.
            </EmptyState>
          )}
        </div>
        <div className={styles.pager}>
          <ReservationPagination page={page} pageInfo={pageInfo} onPageChange={changePage} />
        </div>
      </Panel>
      {selected && (
        <ShopApplicationDialog
          application={selected}
          onClose={() => setSelected(null)}
          onDone={handleDone}
        />
      )}
    </div>
  );
}
