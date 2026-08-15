import { buildAdminApiPath } from "./authRouteContract";

function buildAdminAiPath(path: string) {
  return buildAdminApiPath(path);
}

function encodeRouteParam(value: string | number) {
  return encodeURIComponent(String(value));
}

function buildAdminListPath(path: string, page = 0, size = 20) {
  return `${buildAdminAiPath(path)}?page=${page}&size=${size}`;
}



export function buildAdminMcpServerPath(id: string | number) {
  return buildAdminAiPath(`mcp/servers/${encodeRouteParam(id)}`);
}

export function buildAdminMcpServerTestPath(id: string | number) {
  return `${buildAdminMcpServerPath(id)}/test`;
}

export function buildAdminMcpServerRefreshPath(id: string | number) {
  return `${buildAdminMcpServerPath(id)}/refresh`;
}

export const ADMIN_AI_ROUTE_CONTRACT = {

  mcpConfig: buildAdminAiPath("mcp/config"),
  mcpServers: buildAdminAiPath("mcp/servers"),
  audioTtsConfig: buildAdminAiPath("audio/tts-config"),
  exportChatImageConfig: buildAdminAiPath("export/chat-image-config"),
  followUpConfig: buildAdminAiPath("follow-up/config"),
  mermaidConfig: buildAdminAiPath("mermaid/config"),
  multiChatConfig: buildAdminAiPath("multi-chat/config"),
  promptOptimizeConfig: buildAdminAiPath("prompt-optimize/config"),
  multiAgentDiscussionConfig: buildAdminAiPath("multi-chat/multi-agent-discussion/config"),
  researchConfig: buildAdminAiPath("research/config"),
  toolsConfig: buildAdminAiPath("tools/config"),
  translationConfig: buildAdminAiPath("translation/config"),
  voiceChatConfig: buildAdminAiPath("voice-chat/config"),
  webReadConfig: buildAdminAiPath("web-read/config")
} as const;
