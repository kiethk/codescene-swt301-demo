/** Minutes since midnight of a backend time such as "06:30" or "06:30:00". */
export function minutesOf(time: string): number {
  const [hours, minutes] = time.split(':').map(Number);
  return hours * 60 + minutes;
}

export interface StripPosition {
  leftPercent: number;
  widthPercent: number;
}

/** Where a lot sits on a strip that spans the window, as percentages of its width. */
export function stripPosition(start: string, end: string, windowStart: string, windowEnd: string): StripPosition {
  const from = minutesOf(windowStart);
  const length = minutesOf(windowEnd) - from || 1;
  const leftPercent = Math.max(0, ((minutesOf(start) - from) / length) * 100);
  const widthPercent = Math.min(100 - leftPercent, ((minutesOf(end) - minutesOf(start)) / length) * 100);
  return { leftPercent, widthPercent: Math.max(0, widthPercent) };
}

export interface HourMark {
  label: string;
  percent: number;
}

/** One mark per full hour inside the window, for the ruler above the strip. */
export function hourMarks(windowStart: string, windowEnd: string): HourMark[] {
  const from = minutesOf(windowStart);
  const to = minutesOf(windowEnd);
  const length = to - from || 1;
  const marks: HourMark[] = [];
  for (let minute = Math.ceil(from / 60) * 60; minute <= to; minute += 60) {
    marks.push({
      label: `${String(minute / 60).padStart(2, '0')}:00`,
      percent: ((minute - from) / length) * 100,
    });
  }
  return marks;
}
