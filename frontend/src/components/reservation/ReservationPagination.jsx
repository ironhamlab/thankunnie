/**
 * 예약 목록 페이지 이동 (사장님 예약 현황·고객 내 예약 공용). 한 페이지뿐이면 그리지 않는다.
 */
export default function ReservationPagination({ page, pageInfo, onPageChange }) {
  if (pageInfo.totalPages <= 1) {
    return null;
  }
  return (
    <div className="b-flex b-pad">
      <button
        type="button"
        className="b-button b-small"
        disabled={page === 0}
        onClick={() => onPageChange(page - 1)}
      >
        이전
      </button>
      <span>
        {page + 1} / {pageInfo.totalPages}
      </span>
      <button
        type="button"
        className="b-button b-small"
        disabled={!pageInfo.hasNext}
        onClick={() => onPageChange(page + 1)}
      >
        다음
      </button>
    </div>
  );
}
