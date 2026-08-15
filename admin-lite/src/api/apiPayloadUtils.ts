export type RawRecord = Record<string, unknown>;

export function invalidPayloadError(scope: string, reason: string) {
  return new Error(`Invalid ${scope} payload: ${reason}`);
}

export function isRecord(value: unknown): value is RawRecord {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}

export function asString(value: unknown): string | undefined {
  if (typeof value === "string") {
    const trimmed = value.trim();
    return trimmed ? trimmed : undefined;
  }
  if (typeof value === "number" || typeof value === "boolean") {
    return String(value);
  }
  return undefined;
}

export function asNumber(value: unknown): number | undefined {
  if (typeof value === "number" && Number.isFinite(value)) return value;
  if (typeof value === "string" && value.trim()) {
    const parsed = Number(value);
    if (Number.isFinite(parsed)) return parsed;
  }
  return undefined;
}

export function asBoolean(value: unknown): boolean | undefined {
  if (typeof value === "boolean") return value;
  if (value === "true") return true;
  if (value === "false") return false;
  return undefined;
}

export function extractArrayPayload(raw: unknown): unknown[] | null {
  if (Array.isArray(raw)) {
    return raw;
  }
  if (!isRecord(raw)) {
    return null;
  }

  const directCandidates = [raw.items, raw.data, raw.list, raw.records, raw.content];
  for (const candidate of directCandidates) {
    if (Array.isArray(candidate)) {
      return candidate;
    }
  }

  const nestedData = isRecord(raw.data) ? raw.data : null;
  if (nestedData) {
    const nestedCandidates = [nestedData.items, nestedData.list, nestedData.records, nestedData.content];
    for (const candidate of nestedCandidates) {
      if (Array.isArray(candidate)) {
        return candidate;
      }
    }
  }

  return null;
}
