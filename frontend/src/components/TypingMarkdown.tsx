import React, { useState, useEffect } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import './TypingMarkdown.css';

interface Props {
  content: string;
  isStreaming: boolean;
  speed?: number;
}

export const TypingMarkdown: React.FC<Props> = ({ content, isStreaming, speed = 8 }) => {
  const [displayed, setDisplayed] = useState(isStreaming ? '' : content);

  useEffect(() => {
    if (!isStreaming) {
      setDisplayed(content);
      return;
    }
    
    let i = 0;
    const interval = setInterval(() => {
      setDisplayed(content.slice(0, i));
      i++;
      if (i > content.length) {
        clearInterval(interval);
      }
    }, speed);

    return () => clearInterval(interval);
  }, [content, isStreaming, speed]);

  return (
    <div className="typing-markdown">
      <ReactMarkdown remarkPlugins={[remarkGfm]}>{displayed}</ReactMarkdown>
    </div>
  );
};
