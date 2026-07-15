import type { AnalyzeStatus } from "../../hooks/useTaskAnalysis";

type TaskAnalysisControlProps = {
  analyzing: boolean;
  disabled: boolean;
  errorMessage: string;
  onAnalyze: () => void;
  status: AnalyzeStatus;
  successMessage: string;
};

export default function TaskAnalysisControl({
  analyzing,
  disabled,
  errorMessage,
  onAnalyze,
  status,
  successMessage,
}: TaskAnalysisControlProps) {
  return (
    <section className="ml-analyze-row">
      <button disabled={disabled || analyzing} onClick={onAnalyze} type="button">
        {analyzing ? "Analyzing..." : "Analyze with ML"}
      </button>
      {status === "applied" && <span className="ml-analyze-status">{successMessage}</span>}
      {status === "error" && <span className="ml-analyze-status is-error">{errorMessage}</span>}
    </section>
  );
}
