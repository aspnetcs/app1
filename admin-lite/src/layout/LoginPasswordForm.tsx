import type { FormEvent } from "react";

export function LoginPasswordForm({
  identifier,
  password,
  disabled,
  loading,
  onIdentifierChange,
  onPasswordChange,
  onSubmit
}: {
  identifier: string;
  password: string;
  disabled: boolean;
  loading: boolean;
  onIdentifierChange: (value: string) => void;
  onPasswordChange: (value: string) => void;
  onSubmit: (event: FormEvent) => void;
}) {
  return (
    <div className="oauth-block">
      <div className="oauth-label">{"\u8d26\u53f7\u5bc6\u7801\u767b\u5f55"}</div>
      <form onSubmit={onSubmit} className="login-form">
        <label>
          {"\u90ae\u7bb1 / \u624b\u673a\u53f7"}
          <input
            value={identifier}
            onChange={(event) => onIdentifierChange(event.target.value)}
            placeholder="admin@webchat.com"
          />
        </label>
        <label>
          {"\u5bc6\u7801"}
          <input
            type="password"
            value={password}
            onChange={(event) => onPasswordChange(event.target.value)}
            placeholder={"\u8f93\u5165\u8d26\u53f7\u5bc6\u7801"}
          />
        </label>
        <button type="submit" disabled={disabled}>
          {loading ? "\u767b\u5f55\u4e2d..." : "\u5bc6\u7801\u767b\u5f55"}
        </button>
      </form>
      <div className="login-meta">
        {"\u5bc6\u7801\u767b\u5f55\u4f1a\u901a\u8fc7 platform-api \u9a8c\u8bc1\u8d26\u53f7\u5bc6\u7801\uff0c\u6210\u529f\u540e\u81ea\u52a8\u83b7\u53d6\u4f1a\u8bdd\u3002"}
      </div>
    </div>
  );
}
