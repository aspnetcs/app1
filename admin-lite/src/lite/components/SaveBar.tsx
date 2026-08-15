import type { SaveStatus } from "../hooks/useSaveState";

type SaveBarProps = {
  status: SaveStatus;
  errorMessage?: string | null;
  onSave: () => void;
  onDelete?: () => void;
  saveLabel?: string;
  deleteLabel?: string;
};

export function SaveBar({
  status,
  errorMessage,
  onSave,
  onDelete,
  saveLabel = "保存",
  deleteLabel = "删除",
}: SaveBarProps) {
  const saving = status === "saving";

  return (
    <div className="lite-save-bar">
      <button
        type="button"
        className="lite-btn lite-btn-primary"
        onClick={onSave}
        disabled={saving}
      >
        {saving ? "正在保存..." : saveLabel}
      </button>
      {onDelete ? (
        <button
          type="button"
          className="lite-btn lite-btn-danger"
          onClick={onDelete}
          disabled={saving}
        >
          {deleteLabel}
        </button>
      ) : null}
      {status === "success" ? (
        <span className="text-green-600 dark:text-green-400 text-sm">已保存</span>
      ) : null}
      {status === "error" ? (
        <span className="text-red-600 dark:text-red-400 text-sm">
          {errorMessage ?? "保存失败"}
        </span>
      ) : null}
    </div>
  );
}
