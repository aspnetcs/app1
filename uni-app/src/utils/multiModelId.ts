/**
 * Multi-model ID utility functions.
 * Extracted to main package to avoid cross-subpackage import issues in WeChat MP.
 * Original source: chat/pages/index/chatSendState.ts
 */

export function replaceMultiModelId(
  multiModelIds: string[],
  index: number,
  nextModelId: string,
): string[] {
  if (index < 0 || index >= multiModelIds.length) {
    return [...multiModelIds]
  }

  const normalizedNextModelId = nextModelId.trim()
  if (!normalizedNextModelId) {
    return [...multiModelIds]
  }

  if (multiModelIds[index] === normalizedNextModelId) {
    return [...multiModelIds]
  }

  const duplicatedElsewhere = multiModelIds.some((modelId, currentIndex) => {
    return currentIndex !== index && modelId === normalizedNextModelId
  })
  if (duplicatedElsewhere) {
    return [...multiModelIds]
  }

  const updated = [...multiModelIds]
  updated[index] = normalizedNextModelId
  return updated
}
