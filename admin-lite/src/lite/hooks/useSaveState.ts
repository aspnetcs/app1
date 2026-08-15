import { useState, useCallback } from "react";

export type SaveStatus = "idle" | "saving" | "success" | "error";

export function useSaveState() {
  const [status, setStatus] = useState<SaveStatus>("idle");
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const save = useCallback(async (fn: () => Promise<unknown>) => {
    setStatus("saving");
    setErrorMessage(null);
    try {
      await fn();
      setStatus("success");
      setTimeout(() => setStatus("idle"), 2500);
    } catch (err) {
      setStatus("error");
      setErrorMessage(err instanceof Error ? err.message : "保存失败，请重试");
    }
  }, []);

  const reset = useCallback(() => {
    setStatus("idle");
    setErrorMessage(null);
  }, []);

  return { status, errorMessage, save, reset };
}
