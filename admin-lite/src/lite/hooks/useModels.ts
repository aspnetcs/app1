import { useEffect, useState } from "react";
import { listModels, type ModelItem } from "../../api/models";

export type ModelOption = {
  id: string;
  name: string;
  provider?: string;
  enabled?: boolean;
};

export function useModels() {
  const [models, setModels] = useState<ModelOption[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let mounted = true;
    setLoading(true);
    listModels({ size: 200 })
      .then((response) => {
        if (!mounted) return;
        const options: ModelOption[] = response.items
          .filter((m: ModelItem) => m.enabled !== false)
          .map((m: ModelItem) => ({
            id: m.id,
            name: m.name,
            provider: m.provider,
            enabled: m.enabled,
          }));
        setModels(options);
      })
      .catch((err) => {
        if (!mounted) return;
        setError(err instanceof Error ? err.message : "加载模型失败");
      })
      .finally(() => {
        if (!mounted) return;
        setLoading(false);
      });
    return () => { mounted = false; };
  }, []);

  return { models, loading, error };
}
