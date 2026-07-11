const enMonthShort = new Intl.DateTimeFormat("en-US", { month: "short" });
const enMonthLong = new Intl.DateTimeFormat("en-US", { month: "long" });
const enTime = new Intl.DateTimeFormat("en-US", {
  hour: "numeric",
  minute: "2-digit",
});

function getCalendarDayLabel(date: Date) {
  const startOfToday = new Date();
  startOfToday.setHours(0, 0, 0, 0);

  const startOfDate = new Date(date);
  startOfDate.setHours(0, 0, 0, 0);

  const dayDiff = Math.round((startOfDate.getTime() - startOfToday.getTime()) / 86_400_000);

  if (dayDiff === -1) {
    return "yesterday";
  }

  if (dayDiff === 0) {
    return "today";
  }

  if (dayDiff === 1) {
    return "tomorrow";
  }

  return "";
}

export function formatDateTime(value: string | null | undefined) {
  return formatCalendarDateTime(value, { compact: false, prefix: "" });
}

export function formatDeadline(value: string | null | undefined) {
  const parts = getDateTimeParts(value, false);
  return parts.time ? `Deadline: ${parts.date} · ${parts.time}` : parts.date;
}

export function formatKanbanDeadline(value: string | null | undefined) {
  const parts = getDateTimeParts(value, true);
  return parts.time ? `${parts.date} · ${parts.time}` : parts.date;
}

export function getDeadlineParts(value: string | null | undefined, compact = false) {
  return getDateTimeParts(value, compact, true);
}

function formatCalendarDateTime(
  value: string | null | undefined,
  options: { compact: boolean; prefix: string },
) {
  if (!value) {
    return options.prefix ? "No due date" : "No date";
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }

  const calendarLabel = getCalendarDayLabel(date);
  const prefix = options.prefix ? `${options.prefix} ` : "";
  const time = enTime.format(date);

  if (calendarLabel) {
    return options.compact
      ? `${prefix}${calendarLabel} ${time}`
      : `${prefix}${calendarLabel} at ${time}`;
  }

  const now = new Date();
  const month = (options.compact ? enMonthShort : enMonthLong).format(date);
  const year = date.getFullYear() !== now.getFullYear() ? `, ${date.getFullYear()}` : "";
  const dateText = options.compact
    ? `${month} ${date.getDate()}${year}`
    : `${month} ${date.getDate()}${year}`;

  return options.compact
    ? `${prefix}${dateText} ${time}`
    : `${prefix}${dateText} at ${time}`;
}

function getDateTimeParts(value: string | null | undefined, compact: boolean, alwaysShowYear = false) {
  if (!value) {
    return { date: "No due date", time: "" };
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return { date: value, time: "" };
  }

  const calendarLabel = getCalendarDayLabel(date);
  const now = new Date();
  const month = (compact ? enMonthShort : enMonthLong).format(date);
  const year = alwaysShowYear || date.getFullYear() !== now.getFullYear() ? `, ${date.getFullYear()}` : "";
  const dateText = calendarLabel
    ? `${calendarLabel[0].toUpperCase()}${calendarLabel.slice(1)}, ${month} ${date.getDate()}${year}`
    : `${month} ${date.getDate()}${year}`;

  return {
    date: dateText,
    time: enTime.format(date),
  };
}

export function toApiDateTime(value: string) {
  if (!value) {
    return "";
  }

  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toISOString();
}

export function toDateTimeLocalInput(value: string | null | undefined) {
  if (!value) {
    return "";
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "";
  }

  const offsetMs = date.getTimezoneOffset() * 60_000;
  return new Date(date.getTime() - offsetMs).toISOString().slice(0, 16);
}
