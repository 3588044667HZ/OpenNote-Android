(function() {
  'use strict';

  var nativeBridge = window.__nativeBridge || {
    postMessage: function(json) {
      try {
        var msg = JSON.parse(json);
        if (window.__nativeBridgeReal) {
          window.__nativeBridgeReal.postMessage(json);
        }
      } catch(e) {}
    }
  };

  var editorInitialized = false;

  function htmlToMarkdown(html) {
    var md = html || '';
    md = md.replace(/<h1>(.*?)<\/h1>/gi, '# $1\n\n');
    md = md.replace(/<h2>(.*?)<\/h2>/gi, '## $1\n\n');
    md = md.replace(/<h3>(.*?)<\/h3>/gi, '### $1\n\n');
    md = md.replace(/<p>(.*?)<\/p>/gi, '$1\n\n');
    md = md.replace(/<strong>(.*?)<\/strong>/gi, '**$1**');
    md = md.replace(/<b>(.*?)<\/b>/gi, '**$1**');
    md = md.replace(/<em>(.*?)<\/em>/gi, '*$1*');
    md = md.replace(/<i>(.*?)<\/i>/gi, '*$1*');
    md = md.replace(/<u>(.*?)<\/u>/gi, '<u>$1</u>');
    md = md.replace(/<s>(.*?)<\/s>/gi, '~~$1~~');
    md = md.replace(/<del>(.*?)<\/del>/gi, '~~$1~~');
    md = md.replace(/<code>(.*?)<\/code>/gi, '`$1`');
    md = md.replace(/<pre><code>(.*?)<\/code><\/pre>/gi, '```\n$1\n```\n');
    md = md.replace(/<ul>(.*?)<\/ul>/gi, function(m, content) { return content.replace(/<li>(.*?)<\/li>/gi, '- $1\n'); });
    md = md.replace(/<ol>(.*?)<\/ol>/gi, function(m, content) {
      var idx = 1;
      return content.replace(/<li>(.*?)<\/li>/gi, function(_, li) { return (idx++) + '. ' + li + '\n'; });
    });
    md = md.replace(/<blockquote>(.*?)<\/blockquote>/gi, function(m, content) {
      return content.split('\n').map(function(l) { return '> ' + l; }).join('\n') + '\n';
    });
    md = md.replace(/<a href="(.*?)">(.*?)<\/a>/gi, '[$2]($1)');
    md = md.replace(/<hr\s*\/?>/gi, '---\n');
    md = md.replace(/<br\s*\/?>/gi, '\n');
    md = md.replace(/<[^>]+>/g, '');
    md = md.replace(/&nbsp;/g, ' ');
    md = md.replace(/&amp;/g, '&');
    md = md.replace(/&lt;/g, '<');
    md = md.replace(/&gt;/g, '>');
    md = md.replace(/&quot;/g, '"');
    return md.trim();
  }

  function markdownToHtml(md) {
    var html = md || '';
    html = html.replace(/&/g, '&amp;');
    html = html.replace(/</g, '&lt;');
    html = html.replace(/>/g, '&gt;');
    var lines = html.split('\n');
    var result = [];
    var inList = false;
    var inOList = false;

    for (var i = 0; i < lines.length; i++) {
      var line = lines[i];
      if (/^### (.+)/.test(line)) {
        result.push('<h3>' + processInline(line.replace(/^### /, '')) + '</h3>');
      } else if (/^## (.+)/.test(line)) {
        result.push('<h2>' + processInline(line.replace(/^## /, '')) + '</h2>');
      } else if (/^# (.+)/.test(line)) {
        result.push('<h1>' + processInline(line.replace(/^# /, '')) + '</h1>');
      } else if (/^\- (.+)/.test(line)) {
        if (inOList) { result.push('</ol>'); inOList = false; }
        if (!inList) { result.push('<ul>'); inList = true; }
        result.push('<li>' + processInline(line.replace(/^\- /, '')) + '</li>');
      } else if (/^\d+\. (.+)/.test(line)) {
        if (inList) { result.push('</ul>'); inList = false; }
        if (!inOList) { result.push('<ol>'); inOList = true; }
        result.push('<li>' + processInline(line.replace(/^\d+\. /, '')) + '</li>');
      } else if (/^> (.+)/.test(line)) {
        if (inList) { result.push('</ul>'); inList = false; }
        if (inOList) { result.push('</ol>'); inOList = false; }
        result.push('<blockquote>' + processInline(line.replace(/^> /, '')) + '</blockquote>');
      } else if (line.trim() === '---') {
        if (inList) { result.push('</ul>'); inList = false; }
        if (inOList) { result.push('</ol>'); inOList = false; }
        result.push('<hr>');
      } else if (line.trim() === '') {
        if (inList) { result.push('</ul>'); inList = false; }
        if (inOList) { result.push('</ol>'); inOList = false; }
      } else {
        if (inList) { result.push('</ul>'); inList = false; }
        if (inOList) { result.push('</ol>'); inOList = false; }
        result.push('<p>' + processInline(line) + '</p>');
      }
    }
    if (inList) result.push('</ul>');
    if (inOList) result.push('</ol>');
    return result.join('\n');
  }

  function processInline(text) {
    text = text.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');
    text = text.replace(/__(.+?)__/g, '<strong>$1</strong>');
    text = text.replace(/\*(.+?)\*/g, '<em>$1</em>');
    text = text.replace(/_(.+?)_/g, '<em>$1</em>');
    text = text.replace(/~~(.+?)~~/g, '<s>$1</s>');
    text = text.replace(/`(.+?)`/g, '<code>$1</code>');
    text = text.replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2">$1</a>');
    return text;
  }

  function initEditor() {
    var editorEl = document.getElementById('editor');
    if (!editorEl) return;

    editorEl.contentEditable = 'true';
    editorEl.classList.add('ProseMirror');

    document.execCommand('defaultParagraphSeparator', false, 'p');

    editorEl.addEventListener('input', function() {
      var html = editorEl.innerHTML;
      var md = htmlToMarkdown(html);
      nativeBridge.postMessage(JSON.stringify({
        type: 'contentChange',
        markdown: md,
        length: md.length
      }));
    });

    editorInitialized = true;
    nativeBridge.postMessage(JSON.stringify({ type: 'editorReady' }));
  }

  window.editor = {
    chain: function() { return this; },
    focus: function() { return this; },
    toggleBold: function() { document.execCommand('bold', false, null); return this; },
    toggleItalic: function() { document.execCommand('italic', false, null); return this; },
    toggleUnderline: function() { document.execCommand('underline', false, null); return this; },
    toggleStrike: function() { document.execCommand('strikeThrough', false, null); return this; },
    toggleBulletList: function() { document.execCommand('insertUnorderedList', false, null); return this; },
    toggleOrderedList: function() { document.execCommand('insertOrderedList', false, null); return this; },
    toggleBlockquote: function() { document.execCommand('formatBlock', false, 'blockquote'); return this; },
    toggleHeading: function(opts) { document.execCommand('formatBlock', false, 'h' + (opts ? opts.level : 1)); return this; },
    run: function() {}
  };

  window.__setContent = function(data) {
    var el = document.getElementById('editor');
    if (el && data && data.content) {
      el.innerHTML = markdownToHtml(data.content);
    }
  };

  window.__setReadonly = function(flag) {
    var el = document.getElementById('editor');
    if (el) el.contentEditable = flag ? 'false' : 'true';
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initEditor);
  } else {
    initEditor();
  }

  if (window.__nativeBridgeReal) {
    window.__nativeBridge = window.__nativeBridgeReal;
  }
})();
