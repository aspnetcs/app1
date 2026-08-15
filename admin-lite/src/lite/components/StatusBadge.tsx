type StatusBadgeProps = {
  enabled?: boolean;
  activeLabel?: string;
  inactiveLabel?: string;
};

export function StatusBadge({
  enabled,
  activeLabel = "启用",
  inactiveLabel = "停用",
}: StatusBadgeProps) {
  return (
    <span className={`lite-badge ${enabled ? "lite-badge-active" : "lite-badge-inactive"}`}>
      {enabled ? activeLabel : inactiveLabel}
    </span>
  );
}
