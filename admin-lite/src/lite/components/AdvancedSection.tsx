import { useState, type ReactNode } from "react";
import { ChevronDown, ChevronRight } from "lucide-react";

type AdvancedSectionProps = {
  label?: string;
  children: ReactNode;
  defaultOpen?: boolean;
};

export function AdvancedSection({
  label = "高级选项",
  children,
  defaultOpen = false,
}: AdvancedSectionProps) {
  const [open, setOpen] = useState(defaultOpen);

  return (
    <div>
      <button
        type="button"
        className="lite-advanced-toggle"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
      >
        {open ? (
          <ChevronDown size={14} />
        ) : (
          <ChevronRight size={14} />
        )}
        {label}
      </button>
      {open ? (
        <div className="lite-advanced-content">{children}</div>
      ) : null}
    </div>
  );
}
