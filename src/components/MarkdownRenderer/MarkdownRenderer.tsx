import type { ReactNode } from "react";
import "./MarkdownRenderer.css";

type MarkdownRendererProps = {
  value: string | null | undefined;
  emptyText?: string;
};

type ListItem = {
  text: string;
  checked?: boolean;
};

function isSafeUrl(url: string) {
  return url.startsWith("http://") || url.startsWith("https://") || url.startsWith("/");
}

function renderInline(text: string, keyPrefix: string): ReactNode[] {
  const nodes: ReactNode[] = [];
  const pattern = /(\*\*[^*]+\*\*|\*[^*]+\*|`[^`]+`|\[[^\]]+\]\([^)]+\))/g;
  let cursor = 0;
  let match: RegExpExecArray | null;

  while ((match = pattern.exec(text)) !== null) {
    if (match.index > cursor) {
      nodes.push(text.slice(cursor, match.index));
    }

    const token = match[0];
    const key = `${keyPrefix}-${match.index}`;

    if (token.startsWith("**")) {
      nodes.push(<strong key={key}>{token.slice(2, -2)}</strong>);
    } else if (token.startsWith("*")) {
      nodes.push(<em key={key}>{token.slice(1, -1)}</em>);
    } else if (token.startsWith("`")) {
      nodes.push(<code key={key}>{token.slice(1, -1)}</code>);
    } else {
      const linkMatch = token.match(/^\[([^\]]+)\]\(([^)]+)\)$/);
      const label = linkMatch?.[1] ?? token;
      const url = linkMatch?.[2] ?? "";
      nodes.push(
        isSafeUrl(url) ? (
          <a href={url} key={key} rel="noreferrer" target={url.startsWith("/") ? undefined : "_blank"}>
            {label}
          </a>
        ) : (
          label
        ),
      );
    }

    cursor = match.index + token.length;
  }

  if (cursor < text.length) {
    nodes.push(text.slice(cursor));
  }

  return nodes;
}

function parseTaskListItem(text: string): ListItem {
  const taskMatch = text.match(/^\[( |x|X)\]\s+(.+)$/);
  if (!taskMatch) {
    return { text };
  }

  return {
    checked: taskMatch[1].toLowerCase() === "x",
    text: taskMatch[2],
  };
}

export default function MarkdownRenderer({ value, emptyText = "No description" }: MarkdownRendererProps) {
  const lines = (value ?? "").split("\n");
  const blocks: ReactNode[] = [];
  let unorderedItems: ListItem[] = [];
  let orderedItems: ListItem[] = [];
  let codeLines: string[] = [];
  let codeFence = "";

  function flushUnorderedList(index: number) {
    if (unorderedItems.length === 0) {
      return;
    }

    blocks.push(
      <ul className={unorderedItems.some((item) => item.checked !== undefined) ? "task-list" : undefined} key={`ul-${index}`}>
        {unorderedItems.map((item, itemIndex) => (
          <li className={item.checked !== undefined ? "task-list-item" : undefined} key={`${index}-${itemIndex}`}>
            {item.checked !== undefined && <input checked={item.checked} readOnly type="checkbox" />}
            <span>{renderInline(item.text, `${index}-${itemIndex}`)}</span>
          </li>
        ))}
      </ul>,
    );
    unorderedItems = [];
  }

  function flushOrderedList(index: number) {
    if (orderedItems.length === 0) {
      return;
    }

    blocks.push(
      <ol key={`ol-${index}`}>
        {orderedItems.map((item, itemIndex) => (
          <li key={`${index}-${itemIndex}`}>{renderInline(item.text, `${index}-${itemIndex}`)}</li>
        ))}
      </ol>,
    );
    orderedItems = [];
  }

  function flushLists(index: number) {
    flushUnorderedList(index);
    flushOrderedList(index);
  }

  function flushCodeBlock(index: number) {
    if (!codeFence) {
      return;
    }

    blocks.push(
      <pre key={`code-${index}`}>
        <code>{codeLines.join("\n")}</code>
      </pre>,
    );
    codeFence = "";
    codeLines = [];
  }

  lines.forEach((rawLine, index) => {
    const line = rawLine.trim();

    if (codeFence) {
      if (line.startsWith("```")) {
        flushCodeBlock(index);
        return;
      }

      codeLines.push(rawLine);
      return;
    }

    if (line.startsWith("```")) {
      flushLists(index);
      codeFence = line.slice(3).trim() || "text";
      return;
    }

    if (!line) {
      flushLists(index);
      return;
    }

    if (line === "---" || line === "***") {
      flushLists(index);
      blocks.push(<hr key={index} />);
      return;
    }

    if (line.startsWith("- ") || line.startsWith("* ")) {
      flushOrderedList(index);
      unorderedItems.push(parseTaskListItem(line.slice(2).trim()));
      return;
    }

    const orderedMatch = line.match(/^\d+\.\s+(.+)$/);
    if (orderedMatch) {
      flushUnorderedList(index);
      orderedItems.push({ text: orderedMatch[1] });
      return;
    }

    flushLists(index);

    if (line.startsWith("### ")) {
      blocks.push(<h4 key={index}>{renderInline(line.slice(4), `${index}`)}</h4>);
      return;
    }

    if (line.startsWith("## ")) {
      blocks.push(<h3 key={index}>{renderInline(line.slice(3), `${index}`)}</h3>);
      return;
    }

    if (line.startsWith("# ")) {
      blocks.push(<h2 key={index}>{renderInline(line.slice(2), `${index}`)}</h2>);
      return;
    }

    if (line.startsWith("> ")) {
      blocks.push(<blockquote key={index}>{renderInline(line.slice(2), `${index}`)}</blockquote>);
      return;
    }

    blocks.push(<p key={index}>{renderInline(line, `${index}`)}</p>);
  });

  flushLists(lines.length);
  flushCodeBlock(lines.length);

  if (blocks.length === 0) {
    return <p className="markdown-empty">{emptyText}</p>;
  }

  return <section className="markdown-renderer">{blocks}</section>;
}
