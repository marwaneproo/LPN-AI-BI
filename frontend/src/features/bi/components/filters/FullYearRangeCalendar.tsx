import { buildCalendarMonthDays } from "../../../../utils/dateUtils";
import { analysisMonthLabels, analysisWeekdayLabels } from "../../constants/bi.constants";

export function FullYearRangeCalendar({
  year,
  fromDate,
  toDate,
  pendingStartDate,
  onDateClick,
}: {
  year: number;
  fromDate: string;
  toDate: string;
  pendingStartDate: string | null;
  onDateClick: (dateIso: string) => void;
}) {
  const activeStart = pendingStartDate ?? fromDate;
  const activeEnd = pendingStartDate ? pendingStartDate : toDate;

  return (
    <div className="analysis-year-calendar" aria-label={`Calendrier complet ${year}`}>
      {analysisMonthLabels.map((monthLabel, monthIndex) => {
        const monthDays = buildCalendarMonthDays(year, monthIndex);
        return (
          <section className="analysis-month-card" key={monthLabel} aria-label={`${monthLabel} ${year}`}>
            <header>{monthLabel}</header>
            <div className="analysis-weekdays" aria-hidden="true">
              {analysisWeekdayLabels.map((weekday) => (
                <span key={weekday}>{weekday}</span>
              ))}
            </div>
            <div className="analysis-month-grid">
              {monthDays.map((day, dayIndex) => {
                if (!day) {
                  return <span className="analysis-calendar-blank" key={`blank-${monthIndex}-${dayIndex}`} aria-hidden="true" />;
                }
                const isBeforePendingStart = Boolean(pendingStartDate && day.iso < pendingStartDate);
                const isSelectedStart = day.iso === activeStart;
                const isSelectedEnd = !pendingStartDate && day.iso === activeEnd;
                const isInRange = Boolean(activeStart && activeEnd && day.iso >= activeStart && day.iso <= activeEnd);
                const className = [
                  "analysis-day-button",
                  isInRange ? "is-in-range" : "",
                  isSelectedStart ? "is-range-start" : "",
                  isSelectedEnd ? "is-range-end" : "",
                  pendingStartDate && day.iso === pendingStartDate ? "is-pending-start" : "",
                ]
                  .filter(Boolean)
                  .join(" ");

                return (
                  <button
                    className={className}
                    disabled={isBeforePendingStart}
                    key={day.iso}
                    onClick={() => onDateClick(day.iso)}
                    type="button"
                    aria-label={`${day.day} ${monthLabel} ${year}`}
                  >
                    {day.day}
                  </button>
                );
              })}
            </div>
          </section>
        );
      })}
    </div>
  );
}
