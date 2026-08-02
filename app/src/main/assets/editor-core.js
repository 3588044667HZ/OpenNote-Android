(function() {
  'use strict';

  var nativeBridge = window.__nativeBridge || {
    postMessage: function(json) {
      try { var msg = JSON.parse(json); } catch(e) {}
    }
  };
  var titleFocused = false;
  var contentFocused = false;

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
    var inList = false, inOList = false;
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

  function notifyContentChange() {
    var titleEl = document.getElementById('titleEditor');
    var contentEl = document.getElementById('contentEditor');
    var titleText = titleEl ? titleEl.innerText.trim() : '';
    var contentHtml = contentEl ? contentEl.innerHTML : '';
    var contentMd = htmlToMarkdown(contentHtml);
    nativeBridge.postMessage(JSON.stringify({
      type: 'contentChange',
      title: titleText,
      markdown: contentMd,
      length: contentMd.length
    }));
  }

  document.execCommand('defaultParagraphSeparator', false, 'p');

  function init() {
    var titleEl = document.getElementById('titleEditor');
    var contentEl = document.getElementById('contentEditor');
    if (!titleEl || !contentEl) return;

    titleEl.addEventListener('focus', function() { titleFocused = true; });
    titleEl.addEventListener('blur', function() { titleFocused = false; });
    contentEl.addEventListener('focus', function() { contentFocused = true; });
    contentEl.addEventListener('blur', function() { contentFocused = false; });

    titleEl.addEventListener('input', notifyContentChange);

    titleEl.addEventListener('keydown', function(e) {
      if (e.key === 'Enter') {
        e.preventDefault();
        contentEl.focus();
      }
    });

    contentEl.addEventListener('input', notifyContentChange);
  }

  var activeEditor = function() {
    return titleFocused ? document.getElementById('titleEditor') : document.getElementById('contentEditor');
  };

  window.editor = {
    chain: function() { return this; },
    focus: function() { var el = activeEditor(); if (el) el.focus(); return this; },
    toggleBold: function() { var el = activeEditor(); if (el) { el.focus(); document.execCommand('bold', false, null); } return this; },
    toggleItalic: function() { var el = activeEditor(); if (el) { el.focus(); document.execCommand('italic', false, null); } return this; },
    toggleUnderline: function() { var el = activeEditor(); if (el) { el.focus(); document.execCommand('underline', false, null); } return this; },
    toggleStrike: function() { var el = activeEditor(); if (el) { el.focus(); document.execCommand('strikeThrough', false, null); } return this; },
    toggleBulletList: function() { var el = activeEditor(); if (el) { el.focus(); document.execCommand('insertUnorderedList', false, null); } return this; },
    toggleOrderedList: function() { var el = activeEditor(); if (el) { el.focus(); document.execCommand('insertOrderedList', false, null); } return this; },
    toggleBlockquote: function() { var el = activeEditor(); if (el) { el.focus(); document.execCommand('formatBlock', false, 'blockquote'); } return this; },
    toggleHeading: function(opts) { var el = activeEditor(); if (el) { el.focus(); document.execCommand('formatBlock', false, 'h' + (opts ? opts.level : 1)); } return this; },
    run: function() {}
  };

  window.__setContent = function(data) {
    if (data && data.title !== undefined) {
      var titleEl = document.getElementById('titleEditor');
      if (titleEl) titleEl.innerText = data.title || '';
    }
    if (data && data.content) {
      var contentEl = document.getElementById('contentEditor');
      if (contentEl) contentEl.innerHTML = markdownToHtml(data.content);
    }
    var contentEl = document.getElementById('contentEditor');
    if (contentEl) contentEl.focus();
  };

  window.__setReadonly = function(flag) {
    ['titleEditor', 'contentEditor'].forEach(function(id) {
      var el = document.getElementById(id);
      if (el) el.contentEditable = flag ? 'false' : 'true';
    });
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', function() {
      init();
      nativeBridge.postMessage(JSON.stringify({ type: 'editorReady' }));
    });
  } else {
    init();
    nativeBridge.postMessage(JSON.stringify({ type: 'editorReady' }));
  }
})();
