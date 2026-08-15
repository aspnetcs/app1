import type { ReactNode } from "react";

type ThreeColumnProps = {
  list: ReactNode;
  detail: ReactNode;
};

export function ThreeColumn({ list, detail }: ThreeColumnProps) {
  return (
    <div className="lite-three-col">
      <div className="lite-col-list">
        {list}
      </div>
      <div className="lite-col-detail">
        {detail}
      </div>
    </div>
  );
}
