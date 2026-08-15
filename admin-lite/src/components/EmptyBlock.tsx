export function EmptyBlock({ title, detail }: { title: string; detail?: string }) {
  return (
    <div className="state-block empty">
      <div className="state-title">{title}</div>
      {detail ? <div className="state-detail">{detail}</div> : null}
    </div>
  );
}
