export function LoadingBlock({ label = "正在加载..." }: { label?: string }) {
  return (
    <div className="state-block">
      <div className="state-spinner" />
      <div>{label}</div>
    </div>
  );
}
