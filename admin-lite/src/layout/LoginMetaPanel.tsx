export function LoginMetaPanel({
  adminApiBase,
  authApiBase,
  likelyFrontendBase
}: {
  adminApiBase: string;
  authApiBase: string;
  likelyFrontendBase: boolean;
}) {
  return (
    <div className="login-meta">
      <span>{"\u7ba1\u7406 API \u5730\u5740"}</span>
      <strong>{adminApiBase}</strong>
      <span>{"\u8ba4\u8bc1 API \u5730\u5740"}</span>
      <strong>{authApiBase}</strong>
      {likelyFrontendBase ? (
        <span>
          {"\u68c0\u6d4b\u5230\u524d\u7aef\u7aef\u53e3\uff0c\u5f00\u53d1\u73af\u5883\u5efa\u8bae\u540e\u7aef\u5730\u5740\u4e3a 8081/8080\u3002"}
        </span>
      ) : null}
    </div>
  );
}
