import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useOwnerCustomerStore } from '@/store/useOwnerCustomerStore';
import { formatMoney, formatReservationTime } from '@/utils/reservationFormat';
import { datePart } from '@/utils/scheduleDate';
import Alert from '@/components/common/Alert';
import Badge from '@/components/common/Badge';
import Button from '@/components/common/Button';
import Dialog from '@/components/common/Dialog';
import EmptyState from '@/components/common/EmptyState';
import Panel from '@/components/common/Panel';
import Spinner from '@/components/common/Spinner';
import ReservationPagination from '@/components/reservation/ReservationPagination';
import styles from './CustomersPage.module.css';

const PAGE_SIZE = 20;
const MEMO_MAX_LENGTH = 1000;

const SEGMENTS = [
  { key: 'ALL', label: '전체' },
  { key: 'NEW', label: '신규' },
  { key: 'RETURNING', label: '재방문' },
];
const SORTS = [
  { key: 'RECENT', label: '최근 이용 순' },
  { key: 'VISITS', label: '이용 횟수 순' },
  { key: 'AMOUNT', label: '누적 금액 순' },
];
const SEGMENT_LABEL = { NEW: '신규', RETURNING: '재방문' };
const HISTORY_STATUS = {
  COMPLETED: { label: '이용 완료', tone: 'success' },
  UPCOMING: { label: '방문 예정' },
  CANCELLED: { label: '취소', tone: 'neutral' },
};
const CANCEL_TYPE_LABEL = {
  CUSTOMER: '고객 취소',
  CUSTOMER_REQUEST: '요청 대리 취소',
  SHOP_FAULT: '매장 사유 취소',
};

const pick = (value, options, fallback) =>
  options.some((option) => option.key === value) ? value : fallback;

/**
 * 사장님 고객 관리. 이 매장에 예약한 고객의 이용 횟수·최근 이용·누적 금액과 신규·재방문 분류를 보고,
 * 고객을 고르면 예약 이력·요청사항과 매장 메모를 본다.
 * 검색어·분류·정렬·페이지·고른 고객은 주소에 남겨 오늘 예약에서 이름으로 바로 찾아오거나 새로고침해도 유지한다.
 * 목록의 연락처는 서버가 가운데를 가려 주고, 상세에서만 전체 번호를 보여 준다.
 */
export default function CustomersPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const keyword = searchParams.get('keyword') ?? '';
  const segment = pick(searchParams.get('segment'), SEGMENTS, 'ALL');
  const sort = pick(searchParams.get('sort'), SORTS, 'RECENT');
  const page = Math.max(0, Number.parseInt(searchParams.get('page') ?? '0', 10) || 0);
  const selectedId = Number.parseInt(searchParams.get('customer') ?? '', 10) || null;

  const customers = useOwnerCustomerStore((state) => state.customers);
  const pageInfo = useOwnerCustomerStore((state) => state.pageInfo);
  const listStatus = useOwnerCustomerStore((state) => state.listStatus);
  const loadCustomers = useOwnerCustomerStore((state) => state.loadCustomers);
  const loadCustomer = useOwnerCustomerStore((state) => state.loadCustomer);
  const clearCustomer = useOwnerCustomerStore((state) => state.clearCustomer);
  const [draftKeyword, setDraftKeyword] = useState(keyword);

  useEffect(() => {
    setDraftKeyword(keyword);
  }, [keyword]);

  useEffect(() => {
    loadCustomers({ segment, sort, keyword, page, size: PAGE_SIZE });
  }, [loadCustomers, segment, sort, keyword, page]);

  useEffect(() => {
    if (selectedId) {
      loadCustomer(selectedId);
    } else {
      clearCustomer();
    }
  }, [loadCustomer, clearCustomer, selectedId]);

  // 조건을 바꾸면 첫 페이지로 돌아간다. 고른 고객은 그대로 둔다
  const update = (changes, { resetPage = true } = {}) => {
    const next = new URLSearchParams(searchParams);
    Object.entries(changes).forEach(([key, value]) => {
      if (value === null || value === '' || value === undefined) next.delete(key);
      else next.set(key, String(value));
    });
    if (resetPage) next.delete('page');
    setSearchParams(next, { replace: true });
  };

  const retry = () => loadCustomers({ segment, sort, keyword, page, size: PAGE_SIZE });

  return (
    <div className="b-stack">
      <header>
        <h1 className={styles.title}>고객 관리</h1>
        <p className="b-muted">
          우리 매장에 예약한 고객이에요. 이용은 끝난 확정 예약 기준이고, 금액은 예약 시점
          가격이에요.
        </p>
      </header>

      <div className={styles.layout}>
        <Panel as="section" padded className={`b-stack ${styles.list}`} aria-label="고객 목록">
          <div className={styles.toolbar}>
            <div className="b-options" role="group" aria-label="고객 분류">
              {SEGMENTS.map((item) => (
                <button
                  key={item.key}
                  type="button"
                  className={segment === item.key ? 'is-selected' : ''}
                  aria-pressed={segment === item.key}
                  onClick={() => update({ segment: item.key === 'ALL' ? null : item.key })}
                >
                  {item.label}
                </button>
              ))}
            </div>
            <form
              className={styles.search}
              role="search"
              onSubmit={(event) => {
                event.preventDefault();
                update({ keyword: draftKeyword.trim() });
              }}
            >
              <label className="b-visually-hidden" htmlFor="customer-keyword">
                이름 또는 연락처 뒷자리
              </label>
              <input
                id="customer-keyword"
                type="search"
                value={draftKeyword}
                placeholder="이름 · 연락처 뒷자리"
                onChange={(event) => setDraftKeyword(event.target.value)}
              />
              <Button size="small" type="submit">
                찾기
              </Button>
            </form>
            <label className={styles.sort}>
              <span className="b-visually-hidden">정렬</span>
              <select
                value={sort}
                onChange={(event) =>
                  update({ sort: event.target.value === 'RECENT' ? null : event.target.value })
                }
              >
                {SORTS.map((item) => (
                  <option key={item.key} value={item.key}>
                    {item.label}
                  </option>
                ))}
              </select>
            </label>
          </div>

          {listStatus === 'failed' && (
            <Alert tone="error" role="alert" className={styles.error}>
              <span>고객 목록을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.</span>
              <Button size="small" type="button" onClick={retry}>
                다시 시도
              </Button>
            </Alert>
          )}
          {listStatus === 'loading' && <Spinner />}
          {listStatus === 'succeeded' && (
            <CustomerTable
              customers={customers}
              total={pageInfo.totalElements}
              selectedId={selectedId}
              hasFilter={Boolean(keyword) || segment !== 'ALL'}
              onSelect={(userId) => update({ customer: userId }, { resetPage: false })}
            />
          )}
          <ReservationPagination
            page={page}
            pageInfo={pageInfo}
            onPageChange={(next) => update({ page: next || null }, { resetPage: false })}
          />
          <p className={styles.hint}>신규: 이용 0~1회 · 재방문: 이용 2회 이상</p>
        </Panel>

        <CustomerDetail onClose={() => update({ customer: null }, { resetPage: false })} />
      </div>
    </div>
  );
}

function CustomerTable({ customers, total, selectedId, hasFilter, onSelect }) {
  if (!customers.length) {
    return (
      <EmptyState>
        {hasFilter
          ? '조건에 맞는 고객이 없어요.'
          : '아직 예약한 고객이 없어요. 첫 예약이 확정되면 여기에 보여요.'}
      </EmptyState>
    );
  }
  return (
    <>
      <p className={styles.count}>{total}명</p>
      <ul className={styles.rows}>
        {customers.map((customer) => (
          <li key={customer.userId}>
            <button
              type="button"
              className={
                customer.userId === selectedId ? `${styles.row} ${styles.selected}` : styles.row
              }
              aria-current={customer.userId === selectedId ? 'true' : undefined}
              onClick={() => onSelect(customer.userId)}
            >
              <span className={styles.who}>
                <strong>{customer.name}</strong>
                <SegmentBadge segment={customer.segment} />
                {customer.hasMemo && <Badge tone="neutral">메모</Badge>}
                <small>{customer.phone}</small>
              </span>
              <span className={styles.numbers}>
                <span>
                  이용 <strong>{customer.visitCount}회</strong>
                </span>
                <span>{formatMoney(customer.totalAmount)}</span>
                <small>
                  {customer.lastVisitAt
                    ? `최근 ${datePart(customer.lastVisitAt)}`
                    : customer.nextReservationAt
                      ? `첫 방문 ${datePart(customer.nextReservationAt)} 예정`
                      : '이용 전'}
                </small>
              </span>
            </button>
          </li>
        ))}
      </ul>
    </>
  );
}

function SegmentBadge({ segment }) {
  return (
    <Badge tone={segment === 'NEW' ? 'success' : undefined}>
      {SEGMENT_LABEL[segment] ?? segment}
    </Badge>
  );
}

function CustomerDetail({ onClose }) {
  const detail = useOwnerCustomerStore((state) => state.detail);
  const detailUserId = useOwnerCustomerStore((state) => state.detailUserId);
  const detailStatus = useOwnerCustomerStore((state) => state.detailStatus);
  const loadCustomer = useOwnerCustomerStore((state) => state.loadCustomer);

  if (detailStatus === 'idle') {
    return (
      <Panel as="aside" padded className={styles.detail}>
        <EmptyState>고객을 고르면 예약 이력과 메모를 볼 수 있어요.</EmptyState>
      </Panel>
    );
  }
  return (
    <Panel as="aside" padded className={`b-stack ${styles.detail}`} aria-label="고객 상세">
      {detailStatus === 'loading' && <Spinner />}
      {detailStatus === 'failed' && (
        <Alert tone="error" role="alert" className={styles.error}>
          <span>고객 정보를 불러오지 못했어요.</span>
          <Button size="small" type="button" onClick={() => loadCustomer(detailUserId)}>
            다시 시도
          </Button>
        </Alert>
      )}
      {detailStatus === 'succeeded' && detail && (
        <>
          <div className="b-row">
            <div>
              <div className={styles.detailName}>
                <h2>{detail.customer.name}</h2>
                <SegmentBadge segment={detail.customer.segment} />
              </div>
              <p className="b-muted">
                {detail.customer.phone}
                {detail.customer.firstVisitAt &&
                  ` · 첫 이용 ${datePart(detail.customer.firstVisitAt)}`}
              </p>
            </div>
            <Button size="small" type="button" onClick={onClose}>
              닫기
            </Button>
          </div>
          <div className={styles.miniStats}>
            <div>
              <small>이용</small>
              <strong>{detail.customer.visitCount}회</strong>
            </div>
            <div>
              <small>누적 금액</small>
              <strong>{formatMoney(detail.customer.totalAmount)}</strong>
            </div>
            <div>
              <small>다음 예약</small>
              <strong>
                {detail.customer.nextReservationAt
                  ? datePart(detail.customer.nextReservationAt)
                  : '-'}
              </strong>
            </div>
          </div>
          <MemoEditor
            key={detail.customer.userId}
            userId={detail.customer.userId}
            memo={detail.memo}
          />
          <History reservations={detail.reservations} />
        </>
      )}
    </Panel>
  );
}

// 입력 중 되돌리기는 브라우저 기본(Ctrl/Cmd+Z)을 쓴다. 저장 값과 입력 값이 같으면 입력칸을 다시 채우지 않아 그 기록을 지키고,
// 저장 전에는 "되돌리기"로 마지막 저장 내용으로 돌아간다. 비우고 저장하면 지울지 한 번 묻는다
function MemoEditor({ userId, memo }) {
  const saveMemo = useOwnerCustomerStore((state) => state.saveMemo);
  const memoStatus = useOwnerCustomerStore((state) => state.memoStatus);
  const memoError = useOwnerCustomerStore((state) => state.memoError);
  const savedText = memo?.memo ?? '';
  const [draft, setDraft] = useState(savedText);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const saving = memoStatus === 'saving';
  const changed = draft.trim() !== savedText;

  const save = async () => {
    const ok = await saveMemo(userId, draft);
    if (ok) {
      setDraft((current) => (current.trim() === current ? current : current.trim()));
    }
  };

  return (
    <section className="b-stack" aria-labelledby="memo-title">
      <label id="memo-title" htmlFor="customer-memo" className={styles.memoLabel}>
        매장 메모 <small>고객에게는 보이지 않아요</small>
      </label>
      <textarea
        id="customer-memo"
        rows={4}
        value={draft}
        maxLength={MEMO_MAX_LENGTH}
        placeholder="예: 누드톤 선호, 큐티클 정리 꼼꼼히"
        onChange={(event) => setDraft(event.target.value)}
      />
      <div className={styles.memoActions}>
        <small className="b-muted">
          {draft.length} / {MEMO_MAX_LENGTH}
          {memo?.updatedAt && ` · ${datePart(memo.updatedAt)} 저장`}
          {memoStatus === 'succeeded' && !changed && ' · 저장했어요'}
        </small>
        <div className="b-flex">
          <Button
            size="small"
            type="button"
            disabled={!changed || saving}
            onClick={() => setDraft(savedText)}
          >
            되돌리기
          </Button>
          <Button
            size="small"
            variant="primary"
            type="button"
            disabled={!changed || saving}
            onClick={() => (draft.trim() === '' ? setConfirmDelete(true) : save())}
          >
            {saving ? '저장 중' : '저장'}
          </Button>
        </div>
      </div>
      {memoStatus === 'failed' && memoError && (
        <Alert tone="error" role="alert">
          메모를 저장하지 못했어요. 잠시 후 다시 시도해 주세요.
        </Alert>
      )}
      {confirmDelete && (
        <Dialog title="메모를 지울까요?" onClose={() => setConfirmDelete(false)}>
          <p>지운 메모는 되돌릴 수 없어요.</p>
          <div className="b-flex">
            <Button type="button" onClick={() => setConfirmDelete(false)}>
              취소
            </Button>
            <Button
              type="button"
              variant="danger"
              onClick={() => {
                setConfirmDelete(false);
                save();
              }}
            >
              지우기
            </Button>
          </div>
        </Dialog>
      )}
    </section>
  );
}

function History({ reservations }) {
  return (
    <section className="b-stack" aria-labelledby="history-title">
      <h3 id="history-title" className={styles.historyTitle}>
        예약 이력 <small>최근 50건</small>
      </h3>
      {reservations.length ? (
        <ul className={styles.history}>
          {reservations.map((item) => {
            const status = HISTORY_STATUS[item.status] ?? { label: item.status };
            return (
              <li key={item.reservationId}>
                <div className="b-row">
                  <strong>{formatReservationTime(item.startsAt, item.endsAt)}</strong>
                  <Badge tone={status.tone}>
                    {item.status === 'CANCELLED' && item.cancelType
                      ? (CANCEL_TYPE_LABEL[item.cancelType] ?? status.label)
                      : status.label}
                  </Badge>
                </div>
                <span className="b-muted">
                  {item.treatmentName} · {formatMoney(item.appliedPrice)}
                </span>
                {item.customerRequest && (
                  <span className={styles.request}>요청사항 · {item.customerRequest}</span>
                )}
              </li>
            );
          })}
        </ul>
      ) : (
        <EmptyState as="p">예약 이력이 없어요.</EmptyState>
      )}
    </section>
  );
}
