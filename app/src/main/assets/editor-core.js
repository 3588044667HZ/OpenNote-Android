(function() {
  'use strict';

  var nativeBridge = window.__nativeBridge || {
    postMessage: function(json) {
      try { JSON.parse(json); } catch(e) {}
    }
  };

  var editor = null;
  var isReady = false;
  var pendingContent = null;

  var TiptapBundle = window.TiptapBundle || {};
  var EditorCtor = TiptapBundle.Editor;
  var StarterKit = TiptapBundle.StarterKit;
  var ImageExt = TiptapBundle.Image;
  var TextColor = TiptapBundle.TextColor;
  var Highlight = TiptapBundle.Highlight;
  var ColoredUnderline = TiptapBundle.ColoredUnderline;

  var COLOR_PRESETS = {
    '--blueColor':   { light: '#1A73E8', dark: '#8AB4F8' },
    '--redColor':    { light: '#EA4335', dark: '#F28B82' },
    '--greenColor':  { light: '#34A853', dark: '#81C995' },
    '--orangeColor': { light: '#FB9600', dark: '#FDD663' },
    '--yellowColor': { light: '#F9AB00', dark: '#FDE293' },
    '--grayColor':   { light: '#5F6368', dark: '#BDC1C6' }
  };

  function notifyChange() {
    if (!editor || !nativeBridge) return;
    var html = editor.getHTML();
    var title = extractTitle(html);
    var contentHtml = stripTitleFromHtml(html);
    nativeBridge.postMessage(JSON.stringify({
      type: 'contentChange',
      title: title,
      html: contentHtml,
      markdown: htmlToMarkdown(html),
      length: contentHtml.length
    }));
  }

  function extractTitle(html) {
    var m = /<h1[^>]*>([\s\S]*?)<\/h1>/i.exec(html || '');
    if (!m) return '';
    var text = m[1].replace(/<[^>]+>/g, '').replace(/&nbsp;/g, ' ').trim();
    return decodeEntities(text);
  }

  function decodeEntities(s) {
    var el = document.createElement('div');
    el.innerHTML = s;
    return el.textContent || '';
  }

  function stripTitleFromHtml(html) {
    return (html || '').replace(/<h1[^>]*>[\s\S]*?<\/h1>/i, '');
  }

  function htmlToMarkdown(html) {
    var md = stripTitleFromHtml(html) || '';
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
    md = md.replace(/<ul>(.*?)<\/ul>/gi, function(m, c) { return c.replace(/<li>(.*?)<\/li>/gi, '- $1\n'); });
    md = md.replace(/<ol>(.*?)<\/ol>/gi, function(m, c) {
      var idx = 1;
      return c.replace(/<li>(.*?)<\/li>/gi, function(_, li) { return (idx++) + '. ' + li + '\n'; });
    });
    md = md.replace(/<blockquote>(.*?)<\/blockquote>/gi, function(m, c) {
      return c.split('\n').map(function(l) { return '> ' + l; }).join('\n') + '\n';
    });
    md = md.replace(/<a href="(.*?)">(.*?)<\/a>/gi, '[$2]($1)');
    md = md.replace(/<hr\s*\/?>/gi, '---\n');
    md = md.replace(/<br\s*\/?>/gi, '\n');
    md = md.replace(/<img[^>]*src="([^"]*)"[^>]*>/gi, function(m, src) {
      var isAttach = /attachId=([^&"]+)/.exec(src) || src.indexOf('/api/attachments/') >= 0;
      if (isAttach) return '![attachment](' + src + ')';
      return '![](' + src + ')';
    });
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
      } else if (/^!\[.*\]\((.+)\)/.test(line)) {
        result.push('<img src="' + line.match(/^!\[.*\]\((.+)\)/)[1] + '" />');
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

  function initEditor() {
    if (editor || !EditorCtor) return;

    var extensions = [];
    if (StarterKit) extensions.push(StarterKit.configure({ heading: { levels: [1, 2, 3] } }));
    if (ImageExt) extensions.push(ImageExt);
    if (TextColor) extensions.push(TextColor);
    if (Highlight) extensions.push(Highlight);
    if (ColoredUnderline) extensions.push(ColoredUnderline);

    try {
      editor = new EditorCtor({
        element: document.getElementById('editor'),
        extensions: extensions,
        content: '<h1></h1><p></p>',
        editorProps: {
          attributes: {
            class: 'ProseMirror',
            'data-placeholder': 'Start writing...'
          }
        },
        onUpdate: function() { notifyChange(); },
        onSelectionUpdate: function() {
          if (!nativeBridge) return;
          var ed = editor;
          var isBold = ed ? ed.isActive('bold') : false;
          var isItalic = ed ? ed.isActive('italic') : false;
          var isUnderline = ed ? ed.isActive('coloredUnderline', { type: 'solid' }) : false;
          var isWavy = ed ? ed.isActive('coloredUnderline', { type: 'wavy' }) : false;
          nativeBridge.postMessage(JSON.stringify({
            type: 'selectionChange',
            isBold: isBold,
            isItalic: isItalic,
            isUnderline: isUnderline,
            isWavy: isWavy
          }));
        }
      });
      isReady = true;
      if (pendingContent) {
        setContentInternal(pendingContent);
        pendingContent = null;
      }
      // 初始化颜色 CSS 变量（跟随系统深浅色）
      window.__setColorVars(
        window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches
      );
      nativeBridge.postMessage(JSON.stringify({ type: 'editorReady' }));
    } catch (e) {
      nativeBridge.postMessage(JSON.stringify({ type: 'editorError', message: String(e) }));
    }
  }

  function setContentInternal(data) {
    if (!editor) return;
    var title = (data && data.title) || '';
    var content = (data && data.content) || '';
    // 兼容两种格式：服务器现在存 HTML，旧数据可能是 Markdown
    var contentHtml = /<[a-z][\s\S]*>/i.test(content) ? content : markdownToHtml(content);
    var html = '<h1>' + escapeHtml(title) + '</h1>' + contentHtml;
    editor.commands.setContent(html || '<h1></h1><p></p>', false);
  }

  function escapeHtml(s) {
    return String(s || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  }

  window.editor = {
    chain: function() { return editor ? editor.chain() : { focus: function() { return this; }, run: function() {} }; },
    isActive: function() { return editor ? editor.isActive.apply(editor, arguments) : false; },
    setTextColor: function(colorVar) {
      if (editor) editor.chain().focus().setTextColor('var(' + colorVar + ')').run();
    },
    unsetTextColor: function() {
      if (editor) editor.chain().focus().unsetTextColor().run();
    },
    isTextColorActive: function(colorVar) {
      return editor ? editor.isActive('textColor', { color: 'var(' + colorVar + ')' }) : false;
    },
    // 高亮：class="highlight_<color>"
    setHighlight: function(colorName) {
      if (editor) editor.chain().focus().setHighlight('highlight_' + colorName).run();
    },
    unsetHighlight: function() {
      if (editor) editor.chain().focus().unsetHighlight().run();
    },
    isHighlightActive: function(colorName) {
      return editor ? editor.isActive('highlight', { className: 'highlight_' + colorName }) : false;
    },
    // 有色下划线：solid / wavy，class="underline_<type>_color_<color>"
    toggleColoredUnderline: function(type, colorName) {
      if (!editor) return;
      var colorKey = 'color_' + colorName;
      var isOn = editor.isActive('coloredUnderline', { type: type, color: colorKey });
      if (isOn) {
        editor.chain().focus().unsetColoredUnderline().run();
      } else {
        editor.chain().focus().toggleColoredUnderline({ type: type, color: colorKey }).run();
      }
    },
    isColoredUnderlineActive: function(type, colorName) {
      return editor
        ? editor.isActive('coloredUnderline', { type: type, color: 'color_' + colorName })
        : false;
    },
    unsetColoredUnderline: function() {
      if (editor) editor.chain().focus().unsetColoredUnderline().run();
    }
  };

  window.__setColorVars = function(darkMode) {
    var root = document.documentElement;
    var keys = Object.keys(COLOR_PRESETS);
    for (var i = 0; i < keys.length; i++) {
      var key = keys[i];
      var preset = COLOR_PRESETS[key];
      root.style.setProperty(key, darkMode ? preset.dark : preset.light);
    }
  };

  var javaHandlers = {
    callInitContentFromJava: function(data) {
      setContentInternal({
        title: data.title || '',
        content: data.content || ''
      });
    },
    callSetTextColorFromJava: function(data) {
      if (data.colorType) {
        window.editor.setTextColor(data.colorType.replace('var(', '').replace(')', ''));
      }
    },
    callSetColorVarsFromJava: function(data) {
      window.__setColorVars(!!data.darkMode);
    },
    callSetReadonlyFromJava: function(data) {
      window.__setReadonly(!!data.flag);
    },
    callInsertImageFromJava: function(data) {
      window.__insertImage(data);
    },
    callSetSkinCssParamsFromJava: function(data) {
      var root = document.documentElement;
      var keys = Object.keys(data || {});
      for (var i = 0; i < keys.length; i++) {
        root.style.setProperty(keys[i], data[keys[i]]);
      }
    }
  };

  window.__handleFromJava = function(json) {
    try {
      var msg = JSON.parse(json);
      var handler = javaHandlers[msg.handlerName];
      if (handler) {
        var data = {};
        try { data = JSON.parse(msg.data); } catch(e) {}
        handler(data);
      }
    } catch (e) {
      if (nativeBridge) nativeBridge.postMessage(JSON.stringify({
        type: 'bridgeError', message: String(e)
      }));
    }
  };

  window.__setContent = function(data) {
    if (!editor) { pendingContent = data; return; }
    setContentInternal(data);
  };

  window.__insertImage = function(data) {
    if (!editor || !data) return;
    var src = data.src || '';
    var attachId = data.attachId || '';
    // 两阶段方案下 src 已是 /api/attachments/{uuid}/download?size=thumb，无需再拼 attachId
    if (attachId && src.indexOf('attachId=') < 0) {
      var sep = src.indexOf('?') >= 0 ? '&' : '?';
      src += sep + 'attachId=' + attachId;
    }
    editor.chain().focus().setImage({ src: src, alt: data.alt || '' }).run();
    notifyChange();
  };

  window.__setReadonly = function(flag) {
    if (editor) editor.setEditable(!flag);
  };

  window.__getHtml = function() {
    return editor ? editor.getHTML() : '';
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initEditor);
  } else {
    initEditor();
  }

  // 系统深浅色切换时刷新颜色变量
  if (window.matchMedia) {
    window.matchMedia('(prefers-color-scheme: dark)')
      .addEventListener('change', function(e) {
        window.__setColorVars(e.matches);
      });
  }
})();
