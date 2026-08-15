export function ErrorBlock({ title, detail }: { title: string; detail?: string }) {
  return (
    <div className="state-block error">
      <div className="state-title">{title}</div>
      {detail ? <div className="state-detail">{detail}</div> : null}
    </div>
  );
}
