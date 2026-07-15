import { useCallback, useRef, useState } from "react";
import { analyzeTask } from "../api/tasksApi";
import type { AnalyzeTaskPayload } from "../api/tasksApi";
import type { TaskFieldSuggestion } from "../types/task";

export type AnalyzeStatus = "idle" | "applied" | "error";

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : "Could not get ML suggestions.";
}

export function useTaskAnalysis(applySuggestion: (suggestion: TaskFieldSuggestion) => void) {
  const requestId = useRef(0);
  const [analyzing, setAnalyzing] = useState(false);
  const [status, setStatus] = useState<AnalyzeStatus>("idle");
  const [message, setMessage] = useState("");

  const analyze = useCallback(async (payload: AnalyzeTaskPayload) => {
    const currentRequestId = ++requestId.current;
    setAnalyzing(true);
    setStatus("idle");
    setMessage("");

    try {
      const suggestion = await analyzeTask(payload);
      if (currentRequestId !== requestId.current) {
        return;
      }
      applySuggestion(suggestion);
      setStatus("applied");
    } catch (error) {
      if (currentRequestId !== requestId.current) {
        return;
      }
      setStatus("error");
      setMessage(errorMessage(error));
    } finally {
      if (currentRequestId === requestId.current) {
        setAnalyzing(false);
      }
    }
  }, [applySuggestion]);

  const cancel = useCallback(() => {
    requestId.current += 1;
    setAnalyzing(false);
    setStatus("idle");
    setMessage("");
  }, []);

  return { analyze, analyzing, cancel, message, status };
}
