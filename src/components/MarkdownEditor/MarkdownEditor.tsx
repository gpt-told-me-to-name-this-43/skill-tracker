import { useState } from "react";
import MarkdownRenderer from "../MarkdownRenderer/MarkdownRenderer";
import "./MarkdownEditor.css";

type MarkdownEditorProps = {
  id: string;
  value: string;
  onChange: (value: string) => void;
  name?: string;
  placeholder?: string;
  required?: boolean;
};

export default function MarkdownEditor({
  id,
  value,
  onChange,
  name,
  placeholder,
  required,
}: MarkdownEditorProps) {
  const [tab, setTab] = useState<"write" | "preview">("write");

  return (
    <section className="markdown-editor">
      <header className="markdown-editor-tabs" role="tablist">
        <button
          aria-selected={tab === "write"}
          className={tab === "write" ? "is-active" : ""}
          onClick={() => setTab("write")}
          role="tab"
          type="button"
        >
          Write
        </button>
        <button
          aria-selected={tab === "preview"}
          className={tab === "preview" ? "is-active" : ""}
          onClick={() => setTab("preview")}
          role="tab"
          type="button"
        >
          Preview
        </button>
      </header>

      {tab === "write" ? (
        <textarea
          id={id}
          name={name}
          onChange={(event) => onChange(event.target.value)}
          placeholder={placeholder}
          required={required}
          value={value}
        />
      ) : (
        <section className="markdown-editor-preview">
          <MarkdownRenderer emptyText="Nothing to preview" value={value} />
        </section>
      )}
    </section>
  );
}
