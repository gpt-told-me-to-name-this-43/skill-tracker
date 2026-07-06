import "./ProgressBar.css";

type ProgressBarProps = {
  value: number;
  max?: number;
};

export default function ProgressBar({ value, max = 100 }: ProgressBarProps) {
  const normalizedValue = Math.min(Math.max(value, 0), max);
  const width = `${Math.round((normalizedValue / max) * 100)}%`;

  return (
    <div className="progress-bar" aria-valuemin={0} aria-valuemax={max} aria-valuenow={normalizedValue} role="progressbar">
      <span style={{ width }} />
    </div>
  );
}
