# VS Code Extension Implementation Details (Deminified)

**Source**: openai.chatgpt v0.4.15 - Beautified and analyzed
**Purpose**: Exact implementation details for achieving 1:1 parity in CodexJetbrains

---

## 1. TODO CodeLens Provider - EXACT IMPLEMENTATION

### VS Code TODO Parser (Function `Et`)

```javascript
// Parses TODO comments from source code
function parseTodos(sourceText) {
    let results = [];
    let length = sourceText.length;
    let position = 0;

    // Helper: Process single-line comment (// # --)
    function processSingleLineComment(startPos, commentStart) {
        let commentChar = sourceText[startPos];
        let pos = commentStart;

        // Skip repeating comment characters (e.g., /// or ###)
        while (pos < length && sourceText[pos] === commentChar) pos++;

        let afterComment = skipWhitespace(sourceText, pos);
        let todoIndex = matchKeyword(sourceText, afterComment, "TODO", true);
        let endOfLine = afterComment;

        // Find end of line
        while (endOfLine < length &&
               sourceText[endOfLine] !== '\n' &&
               sourceText[endOfLine] !== '\r') {
            endOfLine++;
        }

        if (todoIndex !== -1) {
            let todoText = sourceText.slice(todoIndex, endOfLine);
            results.push({
                body: todoText.trimEnd(),
                index: startPos  // Character offset in file
            });
        }

        return endOfLine;
    }

    let inString = null;  // Track if inside string literal
    let escaped = false;   // Track escape sequences

    // Main parsing loop
    while (position < length) {
        let char = sourceText[position];

        // Handle string literals (skip TODOs inside strings)
        if (inString) {
            if (escaped) {
                escaped = false;
            } else if (char === '\\') {
                escaped = true;
            } else if (char === inString) {
                inString = null;
            }
            position++;
            continue;
        } else if (char === '"' || char === "'" || char === '`') {
            inString = char;
            position++;
            continue;
        }

        // Check for comment start
        if (char === '/' && position + 1 < length) {
            let nextChar = sourceText[position + 1];

            // Single-line comment: //
            if (nextChar === '/') {
                position = processSingleLineComment(position, position + 2);
                continue;
            }

            // Multi-line comment: /* */
            if (nextChar === '*') {
                let commentStart = position;
                let contentStart = position + 2;
                let endIndex = sourceText.indexOf('*/', contentStart);
                let commentEnd = endIndex === -1 ? length : endIndex + 2;
                let commentContent = sourceText.slice(
                    contentStart,
                    endIndex === -1 ? length : endIndex
                );

                // Remove leading * from each line (common in multi-line comments)
                // Regex: /^(?:[ \t]*\*?[ \t]*(?:\r?\n|$))+/
                commentContent = commentContent.replace(/^(?:[ \t]*\*?[ \t]*(?:\r?\n|$))+/, '');

                let afterLeading = skipWhitespace(commentContent, 0);
                if (commentContent[afterLeading] === '*') {
                    afterLeading = skipWhitespace(commentContent, afterLeading + 1);
                }

                let todoIndex = matchKeyword(commentContent, afterLeading, "TODO", true);
                if (todoIndex !== -1) {
                    let todoText = commentContent.slice(todoIndex);
                    results.push({
                        body: todoText.replace(/\s+$/, ''),  // trim trailing whitespace
                        index: commentStart
                    });
                }

                position = commentEnd;
                continue;
            }
        }
        // Hash comment: #
        else if (char === '#') {
            position = processSingleLineComment(position, position + 1);
            continue;
        }
        // SQL/Lua comment: --
        else if (char === '-' && position + 1 < length && sourceText[position + 1] === '-') {
            position = processSingleLineComment(position, position + 2);
            continue;
        }

        position++;
    }

    return results;
}

// Helper: Check if character is alphanumeric or underscore
function isWordChar(charCode) {
    return (charCode >= 48 && charCode <= 57) ||  // 0-9
           (charCode >= 65 && charCode <= 90) ||  // A-Z
           (charCode >= 97 && charCode <= 122) || // a-z
           charCode === 95;                        // _
}

// Helper: Skip whitespace (space and tab only)
function skipWhitespace(text, startPos) {
    let pos = startPos;
    while (pos < text.length) {
        let charCode = text.charCodeAt(pos);
        if (charCode !== 32 && charCode !== 9) break;  // 32 = space, 9 = tab
        pos++;
    }
    return pos;
}

// Helper: Match keyword (case-insensitive if ignoreCase = true)
function matchKeyword(text, position, keyword, ignoreCase = true) {
    let slice = text.slice(position, position + keyword.length);
    let target = ignoreCase ? keyword.toUpperCase() : keyword;

    if ((ignoreCase ? slice.toUpperCase() : slice) !== target) {
        return -1;
    }

    // Check that next character is not word character (boundary check)
    let nextCharCode = text.charCodeAt(position + keyword.length);
    if (isWordChar(nextCharCode)) {
        return -1;
    }

    // Skip optional : or - after TODO
    let afterKeyword = position + keyword.length;
    if (text[afterKeyword] === ':' || text[afterKeyword] === '-') {
        afterKeyword++;
    }

    // Skip whitespace after : or -
    afterKeyword = skipWhitespace(text, afterKeyword);

    return afterKeyword;
}
```

### VS Code CodeLens Provider

```javascript
class TodoCodeLensProvider {
    provideCodeLenses(document, cancellationToken) {
        let sourceText = document.getText();
        let lenses = [];

        // Parse all TODOs
        for (let { index, body } of parseTodos(sourceText)) {
            // Check for cancellation (performance optimization)
            if (cancellationToken?.isCancellationRequested) break;

            // Convert character offset to Position
            let position = document.positionAt(index);
            let range = new vscode.Range(position, position);  // Zero-width range

            // Create CodeLens with command
            lenses.push(new vscode.CodeLens(range, {
                title: "Implement with Codex",
                command: "chatgpt.implementTodo",
                arguments: [{
                    fileName: encodeURIComponent(document.uri.fsPath),
                    line: position.line + 1,  // 1-indexed for display
                    comment: body
                }]
            }));
        }

        return lenses;
    }
}
```

### Registration (in activate function)

```javascript
// Only register if setting is enabled
if (config.get('commentCodeLensEnabled', true)) {
    let provider = new TodoCodeLensProvider();
    let selector = [
        { scheme: 'file' },
        { scheme: 'untitled' }
    ];
    subscriptions.push(
        vscode.languages.registerCodeLensProvider(selector, provider)
    );
}
```

---

## 2. Editor Highlighting System

### Highlight Management

VS Code maintains highlight state in memory:

```javascript
// Global state
let highlights = {};  // textfieldID -> array of {range, decoration}
let highlightCounters = {};  // textfieldID -> counter for animation sequencing

const HIGHLIGHT_OPACITY = 0.2;  // Yellow background opacity

// Highlight specific lines
async function highlightLines(lines, textfieldID) {
    let editor = getEditorById(textfieldID);
    if (!editor) throw new Error(`No editor for id ${textfieldID} found`);

    let promises = lines.map(lineNumber => {
        let line = editor.document.lineAt(lineNumber);
        let range = new vscode.Range(
            lineNumber, 0,
            lineNumber, line.range.end.character
        );
        return applyHighlight(range, textfieldID);
    });

    await Promise.all(promises);
}

// Highlight character range
async function highlightCharRange(startChar, endChar, textfieldID) {
    let editor = getEditorById(textfieldID);
    if (!editor) throw new Error(`No editor for id ${textfieldID} found`);

    let range = charOffsetToRange(startChar, endChar, editor.document);
    if (range) {
        await applyHighlight(range, textfieldID);
    }
}

// Convert character offsets to Range
function charOffsetToRange(startChar, endChar, document) {
    let offset = 0;
    let startLine = 0, startCol = 0;
    let endLine = 0, endCol = 0;

    for (let lineNum = 0; lineNum < document.lineCount; lineNum++) {
        let lineLength = document.lineAt(lineNum).text.length + 1; // +1 for newline

        if (offset <= startChar && offset + lineLength > startChar) {
            startLine = lineNum;
            startCol = startChar - offset;
        }

        if (offset <= endChar && offset + lineLength > endChar) {
            endLine = lineNum;
            endCol = endChar - offset;
            break;
        }

        offset += lineLength;
    }

    if (startLine >= 0 && endLine >= 0) {
        return new vscode.Range(
            new vscode.Position(startLine, startCol),
            new vscode.Position(endLine, endCol)
        );
    }

    return null;
}

// Apply highlight decoration
async function applyHighlight(range, textfieldID) {
    let editor = getEditorById(textfieldID);
    if (!editor) throw new Error(`No editor for id ${textfieldID} found`);

    // Scroll to show the range
    editor.revealRange(range, vscode.TextEditorRevealType.InCenterIfOutsideViewport);

    // Create decoration
    let decoration = vscode.window.createTextEditorDecorationType({
        backgroundColor: `rgba(255, 255, 0, ${HIGHLIGHT_OPACITY})`,
        isWholeLine: true
    });

    editor.setDecorations(decoration, [range]);

    // Auto-remove on document change
    let listener = vscode.workspace.onDidChangeTextDocument(event => {
        if (event.document === editor.document) {
            decoration.dispose();
            highlights[textfieldID] = [];
            listener.dispose();
        }
    });

    // Store for later removal
    if (!highlights[textfieldID]) {
        highlights[textfieldID] = [];
    }
    highlights[textfieldID].push({ range, decoration });
}

// Remove highlights (with optional fade animation)
async function removeHighlights(textfieldID, animated) {
    let editor = getEditorById(textfieldID);
    if (!editor) throw new Error(`No editor for id ${textfieldID} found`);

    // Initialize counter if needed
    if (!highlightCounters[textfieldID]) {
        highlightCounters[textfieldID] = 1;
    }
    let currentCounter = ++highlightCounters[textfieldID];

    if (animated) {
        let highlightArray = highlights[textfieldID] || [];
        let fadePromises = highlightArray.map(async item => {
            let decoration = item.decoration;
            let steps = 10;
            let stepDuration = 500 / steps;  // 500ms total fade

            try {
                for (let step = 0; step <= steps && currentCounter === highlightCounters[textfieldID]; step++) {
                    let progress = step / steps;
                    let opacity = HIGHLIGHT_OPACITY * (1 - progress);

                    let newDecoration = vscode.window.createTextEditorDecorationType({
                        backgroundColor: `rgba(255, 255, 0, ${opacity})`,
                        isWholeLine: true
                    });

                    editor.setDecorations(newDecoration, [item.range]);
                    decoration?.dispose();
                    decoration = newDecoration;

                    await sleep(stepDuration);
                }
            } finally {
                decoration?.dispose();
            }
        });

        await Promise.all(fadePromises);
    }

    // Remove all decorations
    let highlightArray = highlights[textfieldID];
    if (highlightArray && currentCounter === highlightCounters[textfieldID]) {
        highlightArray.forEach(item => item.decoration.dispose());
        delete highlights[textfieldID];
    }
}

function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}
```

---

## 3. Editor Content Mutation

### Set Full Content

```javascript
async function setContent(content, textfieldID) {
    let editor = getEditorById(textfieldID);
    if (!editor) throw new Error(`No editor for id ${textfieldID} found`);

    let document = editor.document;
    let fullRange = new vscode.Range(
        document.positionAt(0),
        document.positionAt(document.getText().length)
    );

    await editor.edit(editBuilder => {
        editBuilder.replace(fullRange, content);
    });

    // Move cursor to end
    let endPosition = document.positionAt(content.length);
    editor.selection = new vscode.Selection(endPosition, endPosition);
}
```

### Replace Selection

```javascript
async function replaceSelection(content, textfieldID) {
    let editor = getEditorById(textfieldID);
    if (!editor) throw new Error(`No editor for id ${textfieldID} found`);

    let selection = editor.selection;
    let range = new vscode.Range(selection.start, selection.end);

    await editor.edit(editBuilder => {
        editBuilder.replace(range, content);
    });

    // Move cursor to start of replacement
    editor.selection = new vscode.Selection(range.start, range.start);
}
```

---

## 4. Editor Context Retrieval

### Get All Visible Editors Content

```javascript
function getVisibleTextEditors() {
    return vscode.window.visibleTextEditors.filter(
        editor => editor.viewColumn !== undefined
    );
}

function getEditorById(fileName) {
    return getVisibleTextEditors().find(
        editor => editor.document.fileName === fileName
    ) || null;
}

// Get all editor content with selections
function getAllEditorContent() {
    return getVisibleTextEditors().map(editor => {
        let fullText = editor.document.getText();
        let fileName = editor.document.fileName;
        let selectedText = editor.selection.isEmpty ?
            null : editor.document.getText(editor.selection);

        let selectionStart = editor.document.offsetAt(editor.selection.start);
        let selectionEnd = editor.document.offsetAt(editor.selection.end);
        let selectionLine = editor.selection.isEmpty ?
            null : editor.selection.start.line;

        let selectionRange = editor.selection.isEmpty ? null : {
            location: selectionStart,
            length: selectionEnd - selectionStart
        };

        // Don't send selectedText if it's the full document
        if (selectedText === fullText) {
            selectedText = null;
        }

        return {
            id: fileName,
            content: fullText,
            filename: fileName,
            selectedText: selectedText,
            selectionRange: selectionRange,
            selectionLine: selectionLine
        };
    }).filter(item => !!item);
}

// Get only selections (no full content)
function getAllSelections() {
    return getVisibleTextEditors().map(editor => {
        let selectedText = editor.selection.isEmpty ?
            null : editor.document.getText(editor.selection);
        let selectionLine = editor.selection.isEmpty ?
            null : editor.selection.start.line;

        return selectedText != null && selectionLine != null ? {
            selectedText: selectedText,
            selectionLine: selectionLine
        } : null;
    }).filter(item => !!item);
}
```

---

## 5. Command Implementations

### Implement TODO Command

```javascript
vscode.commands.registerCommand('chatgpt.implementTodo', async (args) => {
    let { line, fileName, comment } = args;

    // Open Codex sidebar
    await openCodexSidebar();

    // Create context object
    let todoContext = {
        fileName: fileName,
        line: line,
        comment: comment
    };

    // Send to webview
    if (webviewProvider.sidebarView && webviewProvider.webviewReady) {
        webviewProvider.postMessageToView(webviewProvider.sidebarView.webview, {
            type: 'implement-todo',
            ...todoContext
        });
    } else {
        // Queue for when webview is ready
        webviewProvider.pendingImplementTodo = todoContext;
    }
});
```

### Add to Chat Command

```javascript
async function addToChat(webviewProvider) {
    let editor = vscode.window.activeTextEditor;
    if (!editor) return;

    let document = editor.document;
    if (document.uri.scheme !== 'file') return;

    let selection = editor.selection;
    let startLine = selection.start.line + 1;  // 1-indexed
    let endLine = selection.end.line + 1;

    // If selection ends at column 0, don't include that line
    if (selection.end.character === 0 && selection.end.line > selection.start.line) {
        endLine--;
    }

    let filePath = document.uri.fsPath;
    let fileName = path.basename(filePath);

    // Create label: filename:line or filename:startLine:endLine
    let label = startLine != null ?
        `${fileName}:${startLine}` + (endLine != null && endLine !== startLine ? `:${endLine}` : '') :
        fileName;

    // Open Codex sidebar
    await openCodexSidebar();

    // Add context file
    webviewProvider.addContextFile({
        label: label,
        path: vscode.workspace.asRelativePath(filePath),
        fsPath: filePath,
        startLine: startLine,
        endLine: endLine
    });
}

vscode.commands.registerCommand('chatgpt.addToChat', () => addToChat(webviewProvider));
```

### New Chat Command

```javascript
vscode.commands.registerCommand('chatgpt.newChat', async () => {
    await openCodexSidebar();
    webviewProvider.triggerNewChatViaWebview();
});

// In webview provider:
triggerNewChatViaWebview() {
    if (this.sidebarView && this.webviewReady) {
        this.postMessageToView(this.sidebarView.webview, {
            type: 'new-chat'
        });
    }
}
```

### Open Sidebar Helper

```javascript
async function openCodexSidebar() {
    try {
        // Open view container
        await vscode.commands.executeCommand(
            'workbench.view.extension.codexViewContainer'
        );

        // Focus specific view
        await vscode.commands.executeCommand(
            'chatgpt.sidebarView.focus'
        );
    } catch (error) {
        console.error(`Failed to focus Codex view: ${error.message}`);
    }
}
```

---

## 6. Keybindings (package.json)

```json
{
    "contributes": {
        "keybindings": [
            {
                "command": "chatgpt.newChat",
                "key": "ctrl+n",
                "mac": "cmd+n",
                "when": "focusedView == 'chatgpt.sidebarView'"
            }
        ]
    }
}
```

---

## 7. Context Menus (package.json)

```json
{
    "contributes": {
        "menus": {
            "editor/context": [
                {
                    "command": "chatgpt.addToChat",
                    "when": "editorFocus",
                    "group": "codex"
                }
            ],
            "editor/title/context": [
                {
                    "command": "chatgpt.addToChat",
                    "group": "codex"
                }
            ]
        }
    }
}
```

---

## Summary for CodexJetbrains Implementation

### TODO CodeLens - Critical Implementation Notes

1. **Parser must handle**:
   - String literals (skip TODOs in strings)
   - Single-line comments: `//`, `#`, `--`
   - Multi-line comments: `/* */`
   - Leading `*` in multi-line comments
   - Case-insensitive TODO matching
   - Optional `:` or `-` after TODO
   - Word boundaries (TODO vs TODONT)

2. **Performance**:
   - Single-pass parsing
   - Early cancellation support
   - Character offset → Position conversion

3. **CodeLens placement**:
   - Zero-width range at comment start
   - 1-indexed line numbers for display
   - URI-encode file path in arguments

### IntelliJ Equivalent APIs

| VS Code API | IntelliJ API |
|-------------|--------------|
| `vscode.languages.registerCodeLensProvider()` | `CodeVisionProvider` interface |
| `vscode.CodeLens` | `Pair<TextRange, CodeVisionEntry>` |
| `document.positionAt(offset)` | `Document.getLineNumber(offset)` |
| `vscode.Range(pos, pos)` | `TextRange(offset, offset)` |
| `vscode.commands.registerCommand()` | `AnAction` subclass |
| `vscode.window.createTextEditorDecorationType()` | `RangeHighlighter` via `MarkupModel` |
| `editor.setDecorations()` | `MarkupModel.addRangeHighlighter()` |
| `vscode.workspace.onDidChangeTextDocument()` | `DocumentListener` |

### Key Differences to Account For

1. **IntelliJ Code Vision**:
   - Triggered via `computeCodeVision()` instead of `provideCodeLenses()`
   - Must implement `precomputeOnUiThread()` for any UI thread data
   - Returns `CodeVisionState.Ready` with list of entries

2. **IntelliJ Highlighting**:
   - Uses `MarkupModel` instead of decorations
   - `RangeHighlighter` for persistent highlights
   - `HighlightManager` for temporary highlights
   - Different animation approach (use `AlphaComposite` or custom renderer)

3. **IntelliJ Commands**:
   - `AnAction` with `actionPerformed()` instead of command registration
   - Context data via `AnActionEvent.getData()`
   - Register in `plugin.xml` under `<actions>`

4. **IntelliJ Context Menus**:
   - Add to groups: `EditorPopupMenu`, `EditorTabPopupMenu`
   - Use `<add-to-group>` in plugin.xml
   - Conditional visibility via `update()` method

This should give you everything needed for exact 1:1 implementation!
