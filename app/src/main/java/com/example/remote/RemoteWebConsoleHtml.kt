package com.example.remote

import com.example.model.RemoteServerStatus

object RemoteWebConsoleHtml {

  fun buildHtml(
    deviceModel: String,
    osVer: String,
    supportedAbis: String,
    status: RemoteServerStatus
  ): String {
    return """
<!DOCTYPE html>
<html lang="en" data-theme="light">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>OmniInstaller Remote Studio</title>
  <style>
    :root, [data-theme="light"] {
      --bg-canvas: #f8fafc;
      --bg-surface: #ffffff;
      --bg-elevated: #f1f5f9;
      --bg-subtle: #e2e8f0;
      --bg-glass: rgba(255, 255, 255, 0.88);
      --text-primary: #0f172a;
      --text-secondary: #334155;
      --text-muted: #64748b;
      --border-color: #e2e8f0;
      --border-strong: #cbd5e1;
      --accent: #2563eb;
      --accent-hover: #1d4ed8;
      --accent-soft: rgba(37, 99, 235, 0.08);
      --accent-border: rgba(37, 99, 235, 0.28);
      --success: #059669;
      --success-soft: rgba(5, 150, 105, 0.1);
      --warning: #d97706;
      --warning-soft: rgba(217, 119, 6, 0.1);
      --danger: #dc2626;
      --danger-soft: rgba(220, 38, 38, 0.1);
      --shadow-sm: 0 1px 3px rgba(15, 23, 42, 0.05), 0 1px 2px rgba(15, 23, 42, 0.03);
      --shadow-md: 0 10px 25px -5px rgba(15, 23, 42, 0.06), 0 8px 10px -6px rgba(15, 23, 42, 0.04);
      --shadow-lg: 0 20px 40px -12px rgba(15, 23, 42, 0.12);
      --radius-sm: 10px;
      --radius-md: 14px;
      --radius-lg: 20px;
    }

    [data-theme="dark"] {
      --bg-canvas: #0b0f19;
      --bg-surface: #111827;
      --bg-elevated: #1e293b;
      --bg-subtle: #334155;
      --bg-glass: rgba(17, 24, 39, 0.88);
      --text-primary: #f8fafc;
      --text-secondary: #cbd5e1;
      --text-muted: #94a3b8;
      --border-color: #1f2937;
      --border-strong: #334155;
      --accent: #3b82f6;
      --accent-hover: #2563eb;
      --accent-soft: rgba(59, 130, 246, 0.12);
      --accent-border: rgba(59, 130, 246, 0.35);
      --success: #10b981;
      --success-soft: rgba(16, 185, 129, 0.15);
      --warning: #f59e0b;
      --warning-soft: rgba(245, 158, 11, 0.15);
      --danger: #ef4444;
      --danger-soft: rgba(239, 68, 68, 0.15);
      --shadow-sm: 0 1px 3px rgba(0, 0, 0, 0.3);
      --shadow-md: 0 10px 25px -5px rgba(0, 0, 0, 0.45);
      --shadow-lg: 0 20px 40px -12px rgba(0, 0, 0, 0.65);
    }

    * {
      box-sizing: border-box;
      margin: 0;
      padding: 0;
      font-family: -apple-system, BlinkMacSystemFont, "Inter", "Segoe UI", Roboto, "Hiragino Sans", "Noto Sans JP", sans-serif;
    }

    body {
      background: var(--bg-canvas);
      color: var(--text-primary);
      min-height: 100vh;
      transition: background-color 0.25s ease, color 0.25s ease;
      padding-bottom: 60px;
    }

    svg.icon-svg {
      width: 18px;
      height: 18px;
      stroke: currentColor;
      stroke-width: 2;
      stroke-linecap: round;
      stroke-linejoin: round;
      fill: none;
      flex-shrink: 0;
    }

    /* Top Header */
    .topbar {
      position: sticky;
      top: 0;
      z-index: 40;
      background: var(--bg-glass);
      backdrop-filter: blur(14px);
      -webkit-backdrop-filter: blur(14px);
      border-bottom: 1px solid var(--border-color);
      padding: 14px 24px;
    }
    .topbar-inner {
      max-width: 1200px;
      margin: 0 auto;
      display: flex;
      align-items: center;
      justify-content: space-between;
      flex-wrap: wrap;
      gap: 14px;
    }
    .brand {
      display: flex;
      align-items: center;
      gap: 14px;
    }
    .brand-logo {
      width: 44px;
      height: 44px;
      border-radius: 13px;
      background: linear-gradient(135deg, #2563eb, #06b6d4);
      color: #ffffff;
      display: flex;
      align-items: center;
      justify-content: center;
      box-shadow: 0 6px 16px rgba(37, 99, 235, 0.28);
    }
    .brand-logo svg.icon-svg {
      width: 24px;
      height: 24px;
    }
    .brand-title {
      font-size: 18px;
      font-weight: 800;
      letter-spacing: -0.02em;
      color: var(--text-primary);
    }
    .brand-sub {
      font-size: 12px;
      color: var(--text-muted);
      display: flex;
      align-items: center;
      gap: 8px;
      margin-top: 2px;
    }
    .top-controls {
      display: flex;
      align-items: center;
      gap: 10px;
      flex-wrap: wrap;
    }
    .status-pill {
      display: inline-flex;
      align-items: center;
      gap: 7px;
      background: var(--success-soft);
      color: var(--success);
      font-size: 12px;
      font-weight: 700;
      padding: 6px 12px;
      border-radius: 999px;
      border: 1px solid rgba(16, 185, 129, 0.25);
    }
    .pulse-dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: var(--success);
      animation: pulseAnim 2s infinite;
    }
    @keyframes pulseAnim {
      0% { box-shadow: 0 0 0 0 rgba(16, 185, 129, 0.6); }
      70% { box-shadow: 0 0 0 7px rgba(16, 185, 129, 0); }
      100% { box-shadow: 0 0 0 0 rgba(16, 185, 129, 0); }
    }

    .control-group {
      display: inline-flex;
      align-items: center;
      background: var(--bg-elevated);
      border: 1px solid var(--border-color);
      border-radius: var(--radius-sm);
      padding: 3px;
      gap: 2px;
    }
    .icon-btn {
      background: transparent;
      color: var(--text-secondary);
      border: none;
      padding: 6px 10px;
      border-radius: 7px;
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      gap: 5px;
      font-size: 12px;
      font-weight: 600;
      transition: all 0.18s ease;
    }
    .icon-btn:hover {
      color: var(--text-primary);
      background: var(--bg-surface);
    }
    .icon-btn.active {
      background: var(--bg-surface);
      color: var(--accent);
      box-shadow: var(--shadow-sm);
    }
    select.lang-select {
      background: var(--bg-elevated);
      color: var(--text-primary);
      border: 1px solid var(--border-color);
      border-radius: var(--radius-sm);
      padding: 7px 10px;
      font-size: 12px;
      font-weight: 600;
      cursor: pointer;
      outline: none;
    }

    /* Main Container */
    .main-wrap {
      max-width: 1200px;
      margin: 22px auto 0;
      padding: 0 24px;
    }

    /* Telemetry Strip */
    .telemetry-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
      gap: 14px;
      margin-bottom: 22px;
    }
    .metric-card {
      background: var(--bg-surface);
      border: 1px solid var(--border-color);
      border-radius: var(--radius-md);
      padding: 15px 18px;
      box-shadow: var(--shadow-sm);
      display: flex;
      align-items: center;
      gap: 14px;
      transition: transform 0.2s ease, box-shadow 0.2s ease;
    }
    .metric-card:hover {
      transform: translateY(-2px);
      box-shadow: var(--shadow-md);
    }
    .metric-icon {
      width: 42px;
      height: 42px;
      border-radius: 11px;
      background: var(--accent-soft);
      color: var(--accent);
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .metric-info {
      flex: 1;
      min-width: 0;
    }
    .metric-label {
      font-size: 11px;
      font-weight: 600;
      color: var(--text-muted);
      text-transform: uppercase;
      letter-spacing: 0.03em;
    }
    .metric-val {
      font-size: 15px;
      font-weight: 800;
      color: var(--text-primary);
      margin-top: 2px;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .meter-bar {
      width: 100%;
      height: 5px;
      background: var(--bg-elevated);
      border-radius: 99px;
      overflow: hidden;
      margin-top: 6px;
    }
    .meter-fill {
      height: 100%;
      background: linear-gradient(90deg, var(--accent), #06b6d4);
      border-radius: 99px;
      transition: width 0.4s ease;
    }

    /* Navigation Tabs */
    .nav-tabs {
      display: flex;
      gap: 8px;
      background: var(--bg-surface);
      padding: 6px;
      border-radius: var(--radius-md);
      border: 1px solid var(--border-color);
      box-shadow: var(--shadow-sm);
      margin-bottom: 22px;
      overflow-x: auto;
    }
    .tab-btn {
      flex: 1;
      min-width: max-content;
      background: transparent;
      color: var(--text-secondary);
      border: none;
      padding: 11px 16px;
      border-radius: 10px;
      cursor: pointer;
      font-weight: 700;
      font-size: 13px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
    }
    .tab-btn:hover {
      color: var(--text-primary);
      background: var(--bg-elevated);
    }
    .tab-btn.active {
      background: var(--accent);
      color: #ffffff;
      box-shadow: 0 4px 12px rgba(37, 99, 235, 0.25);
    }

    .panel {
      display: none;
      animation: fadeSlideUp 0.28s cubic-bezier(0.16, 1, 0.3, 1);
    }
    .panel.active {
      display: block;
    }
    @keyframes fadeSlideUp {
      from { opacity: 0; transform: translateY(8px); }
      to { opacity: 1; transform: translateY(0); }
    }

    /* Cards & Layout */
    .grid-2 {
      display: grid;
      grid-template-columns: 1.35fr 1fr;
      gap: 20px;
    }
    @media (max-width: 900px) {
      .grid-2 { grid-template-columns: 1fr; }
    }
    .card {
      background: var(--bg-surface);
      border: 1px solid var(--border-color);
      border-radius: var(--radius-lg);
      padding: 22px;
      box-shadow: var(--shadow-sm);
      margin-bottom: 20px;
    }
    .card-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      margin-bottom: 16px;
      flex-wrap: wrap;
    }
    .card-title {
      display: flex;
      align-items: center;
      gap: 10px;
      font-size: 16px;
      font-weight: 800;
      color: var(--text-primary);
    }
    .card-desc {
      font-size: 12.5px;
      color: var(--text-muted);
      margin-top: 3px;
    }

    /* Dropzone */
    .dropzone {
      border: 2px dashed var(--accent-border);
      background: var(--accent-soft);
      border-radius: 16px;
      padding: 38px 24px;
      text-align: center;
      cursor: pointer;
      transition: all 0.22s ease;
      position: relative;
    }
    .dropzone:hover, .dropzone.dragover {
      border-color: var(--accent);
      background: rgba(37, 99, 235, 0.13);
      transform: scale(1.005);
    }
    .dropzone-circle {
      width: 56px;
      height: 56px;
      border-radius: 50%;
      background: var(--bg-surface);
      color: var(--accent);
      display: flex;
      align-items: center;
      justify-content: center;
      margin: 0 auto 14px;
      box-shadow: var(--shadow-sm);
    }
    .dropzone-circle svg.icon-svg {
      width: 28px;
      height: 28px;
    }
    .format-chips {
      display: flex;
      flex-wrap: wrap;
      justify-content: center;
      gap: 6px;
      margin-top: 14px;
    }
    .fmt-chip {
      font-size: 11px;
      font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
      font-weight: 600;
      background: var(--bg-surface);
      color: var(--accent);
      border: 1px solid var(--border-color);
      padding: 3px 8px;
      border-radius: 6px;
    }

    /* Form Controls & Buttons */
    .form-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
      gap: 12px;
      margin-top: 12px;
    }
    .field-group {
      display: flex;
      flex-direction: column;
      gap: 5px;
    }
    .field-label {
      font-size: 11.5px;
      font-weight: 700;
      color: var(--text-secondary);
    }
    input[type="text"], input[type="number"], select.input-select, textarea.input-area {
      width: 100%;
      background: var(--bg-elevated);
      border: 1px solid var(--border-color);
      color: var(--text-primary);
      padding: 10px 13px;
      border-radius: 10px;
      font-size: 13px;
      outline: none;
      transition: border-color 0.18s ease, background-color 0.18s ease;
    }
    input[type="text"]:focus, input[type="number"]:focus, select.input-select:focus, textarea.input-area:focus {
      border-color: var(--accent);
      background: var(--bg-surface);
    }
    .btn {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 7px;
      background: var(--accent);
      color: #ffffff;
      border: none;
      padding: 10px 16px;
      border-radius: 10px;
      font-weight: 700;
      font-size: 13px;
      cursor: pointer;
      transition: all 0.18s ease;
      text-decoration: none;
    }
    .btn:hover {
      background: var(--accent-hover);
      transform: translateY(-1px);
    }
    .btn:disabled {
      opacity: 0.55;
      cursor: not-allowed;
      transform: none;
    }
    .btn-secondary {
      background: var(--bg-elevated);
      color: var(--text-primary);
      border: 1px solid var(--border-color);
    }
    .btn-secondary:hover {
      background: var(--bg-subtle);
    }
    .btn-success {
      background: var(--success);
      color: #ffffff;
    }
    .btn-success:hover {
      filter: brightness(0.93);
    }
    .btn-danger {
      background: var(--danger-soft);
      color: var(--danger);
      border: 1px solid rgba(220, 38, 38, 0.25);
    }
    .btn-danger:hover {
      background: var(--danger);
      color: #ffffff;
    }
    .btn-sm {
      padding: 7px 11px;
      font-size: 12px;
      border-radius: 8px;
    }

    /* Progress Bar */
    .progress-wrap {
      margin-top: 16px;
      background: var(--bg-elevated);
      border: 1px solid var(--border-color);
      border-radius: 12px;
      padding: 14px;
      display: none;
    }
    .progress-wrap.active {
      display: block;
    }
    .progress-track {
      width: 100%;
      height: 8px;
      background: var(--bg-subtle);
      border-radius: 99px;
      overflow: hidden;
      margin: 8px 0;
    }
    .progress-bar {
      height: 100%;
      width: 0%;
      background: linear-gradient(90deg, var(--accent), #06b6d4);
      border-radius: 99px;
      transition: width 0.2s ease;
    }

    /* App List Cards */
    .toolbar-row {
      display: flex;
      gap: 10px;
      flex-wrap: wrap;
      align-items: center;
      margin-bottom: 16px;
    }
    .search-box {
      flex: 1;
      min-width: 220px;
      position: relative;
      display: flex;
      align-items: center;
    }
    .search-box svg.icon-svg {
      position: absolute;
      left: 12px;
      color: var(--text-muted);
    }
    .search-box input {
      padding-left: 38px;
    }
    .app-grid {
      display: flex;
      flex-direction: column;
      gap: 10px;
    }
    .app-card {
      background: var(--bg-surface);
      border: 1px solid var(--border-color);
      border-radius: 14px;
      padding: 14px 16px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 14px;
      flex-wrap: wrap;
      transition: border-color 0.18s ease, box-shadow 0.18s ease;
    }
    .app-card:hover {
      border-color: var(--accent-border);
      box-shadow: var(--shadow-sm);
    }
    .app-left {
      display: flex;
      align-items: center;
      gap: 14px;
      flex: 1;
      min-width: 240px;
    }
    .app-icon {
      width: 48px;
      height: 48px;
      border-radius: 12px;
      object-fit: cover;
      background: var(--bg-elevated);
      border: 1px solid var(--border-color);
      flex-shrink: 0;
    }
    .app-meta {
      min-width: 0;
      flex: 1;
    }
    .app-name-row {
      display: flex;
      align-items: center;
      gap: 8px;
      flex-wrap: wrap;
    }
    .app-name {
      font-size: 15px;
      font-weight: 800;
      color: var(--text-primary);
    }
    .tag-badge {
      font-size: 10.5px;
      font-weight: 700;
      padding: 2px 8px;
      border-radius: 6px;
      background: var(--accent-soft);
      color: var(--accent);
    }
    .tag-badge.warn {
      background: var(--warning-soft);
      color: var(--warning);
    }
    .app-pkg {
      font-size: 12px;
      font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
      color: var(--text-muted);
      margin-top: 2px;
      word-break: break-all;
    }
    .app-actions {
      display: flex;
      align-items: center;
      gap: 7px;
      flex-wrap: wrap;
    }

    /* Switch Toggle */
    .switch-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 10px 12px;
      background: var(--bg-elevated);
      border-radius: 10px;
      margin-bottom: 8px;
      gap: 12px;
    }
    .toggle-switch {
      position: relative;
      width: 42px;
      height: 24px;
      flex-shrink: 0;
    }
    .toggle-switch input {
      opacity: 0;
      width: 0;
      height: 0;
    }
    .toggle-slider {
      position: absolute;
      cursor: pointer;
      inset: 0;
      background-color: var(--bg-subtle);
      transition: .2s;
      border-radius: 24px;
    }
    .toggle-slider:before {
      position: absolute;
      content: "";
      height: 18px;
      width: 18px;
      left: 3px;
      bottom: 3px;
      background-color: white;
      transition: .2s;
      border-radius: 50%;
    }
    .toggle-switch input:checked + .toggle-slider {
      background-color: var(--accent);
    }
    .toggle-switch input:checked + .toggle-slider:before {
      transform: translateX(18px);
    }

    /* Modals */
    .modal-backdrop {
      position: fixed;
      inset: 0;
      background: rgba(15, 23, 42, 0.55);
      backdrop-filter: blur(5px);
      z-index: 100;
      display: none;
      align-items: center;
      justify-content: center;
      padding: 20px;
    }
    .modal-backdrop.open {
      display: flex;
      animation: fadeIn 0.18s ease;
    }
    .modal-box {
      background: var(--bg-surface);
      border: 1px solid var(--border-color);
      border-radius: 20px;
      width: 100%;
      max-width: 560px;
      max-height: 88vh;
      overflow-y: auto;
      padding: 24px;
      box-shadow: var(--shadow-lg);
    }
    @keyframes fadeIn {
      from { opacity: 0; }
      to { opacity: 1; }
    }

    /* Toast Notifications */
    .toast-stack {
      position: fixed;
      bottom: 22px;
      right: 22px;
      z-index: 120;
      display: flex;
      flex-direction: column;
      gap: 10px;
      max-width: 380px;
    }
    .toast-item {
      background: var(--bg-surface);
      color: var(--text-primary);
      border: 1px solid var(--border-color);
      border-left: 4px solid var(--accent);
      padding: 13px 16px;
      border-radius: 12px;
      box-shadow: var(--shadow-lg);
      display: flex;
      align-items: center;
      gap: 10px;
      font-size: 13px;
      font-weight: 600;
      animation: fadeSlideUp 0.25s ease;
    }

    /* Console Log */
    .log-box {
      background: var(--bg-elevated);
      border: 1px solid var(--border-color);
      border-radius: 12px;
      padding: 14px;
      font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
      font-size: 12px;
      max-height: 300px;
      overflow-y: auto;
      color: var(--text-secondary);
    }
    .log-line {
      padding: 4px 0;
      border-bottom: 1px dashed var(--border-color);
    }
  </style>
</head>
<body>

  <!-- Top Navigation Bar -->
  <header class="topbar">
    <div class="topbar-inner">
      <div class="brand">
        <div class="brand-logo">
          <svg class="icon-svg" viewBox="0 0 24 24"><path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/><polyline points="3.27 6.96 12 12.01 20.73 6.96"/><line x1="12" y1="22.08" x2="12" y2="12"/></svg>
        </div>
        <div>
          <div class="brand-title" data-i18n="appTitle">OmniInstaller Remote Studio</div>
          <div class="brand-sub">
            <svg class="icon-svg" style="width:14px;height:14px;" viewBox="0 0 24 24"><rect x="5" y="2" width="14" height="20" rx="2" ry="2"/><line x1="12" y1="18" x2="12.01" y2="18"/></svg>
            <span>$deviceModel • $osVer • $supportedAbis</span>
          </div>
        </div>
      </div>

      <div class="top-controls">
        <div class="status-pill">
          <span class="pulse-dot"></span>
          <span id="connStatusText" data-i18n="connectedStatus">ONLINE • Port :${status.port}</span>
        </div>

        <!-- Theme Switcher (Light default / Dark / System) -->
        <div class="control-group" role="group" aria-label="Theme">
          <button class="icon-btn active" id="themeLightBtn" onclick="setThemeMode('light')" title="Light Mode">
            <svg class="icon-svg" viewBox="0 0 24 24"><circle cx="12" cy="12" r="5"/><line x1="12" y1="1" x2="12" y2="3"/><line x1="12" y1="21" x2="12" y2="23"/><line x1="4.22" y1="4.22" x2="5.64" y2="5.64"/><line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/><line x1="1" y1="12" x2="3" y2="12"/><line x1="21" y1="12" x2="23" y2="12"/><line x1="4.22" y1="19.78" x2="5.64" y2="18.36"/><line x1="18.36" y1="5.64" x2="19.78" y2="4.22"/></svg>
            <span data-i18n="themeLight">Light</span>
          </button>
          <button class="icon-btn" id="themeDarkBtn" onclick="setThemeMode('dark')" title="Dark Mode">
            <svg class="icon-svg" viewBox="0 0 24 24"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/></svg>
            <span data-i18n="themeDark">Dark</span>
          </button>
          <button class="icon-btn" id="themeAutoBtn" onclick="setThemeMode('auto')" title="System Theme">
            <svg class="icon-svg" viewBox="0 0 24 24"><rect x="2" y="3" width="20" height="14" rx="2" ry="2"/><line x1="8" y1="21" x2="16" y2="21"/><line x1="12" y1="17" x2="12" y2="21"/></svg>
            <span data-i18n="themeAuto">Auto</span>
          </button>
        </div>

        <!-- Language Selector (Auto-detected + manual switch) -->
        <select class="lang-select" id="langSelect" onchange="changeLanguage(this.value)" aria-label="Language">
          <option value="auto">Auto Language</option>
          <option value="ja">日本語 (Japanese)</option>
          <option value="en">English</option>
          <option value="zh">中文 (Chinese)</option>
          <option value="ko">한국어 (Korean)</option>
          <option value="es">Español</option>
          <option value="fr">Français</option>
          <option value="de">Deutsch</option>
        </select>
      </div>
    </div>
  </header>

  <main class="main-wrap">
    <!-- Live Device Telemetry Strip -->
    <section class="telemetry-grid">
      <div class="metric-card">
        <div class="metric-icon">
          <svg class="icon-svg" viewBox="0 0 24 24"><ellipse cx="12" cy="5" rx="9" ry="3"/><path d="M21 12c0 1.66-4 3-9 3s-9-1.34-9-3"/><path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5"/></svg>
        </div>
        <div class="metric-info">
          <div class="metric-label" data-i18n="storageLabel">Device Storage</div>
          <div class="metric-val" id="telemetryStorage">-- / -- GB</div>
          <div class="meter-bar"><div class="meter-fill" id="storageMeter" style="width:35%"></div></div>
        </div>
      </div>

      <div class="metric-card">
        <div class="metric-icon">
          <svg class="icon-svg" viewBox="0 0 24 24"><rect x="4" y="4" width="16" height="16" rx="2" ry="2"/><rect x="9" y="9" width="6" height="6"/><line x1="9" y1="1" x2="9" y2="4"/><line x1="15" y1="1" x2="15" y2="4"/><line x1="9" y1="20" x2="9" y2="23"/><line x1="15" y1="20" x2="15" y2="23"/><line x1="20" y1="9" x2="23" y2="9"/><line x1="20" y1="14" x2="23" y2="14"/><line x1="1" y1="9" x2="4" y2="9"/><line x1="1" y1="14" x2="4" y2="14"/></svg>
        </div>
        <div class="metric-info">
          <div class="metric-label" data-i18n="memoryLabel">System Memory (RAM)</div>
          <div class="metric-val" id="telemetryRam">-- / -- MB</div>
          <div class="meter-bar"><div class="meter-fill" id="ramMeter" style="width:45%"></div></div>
        </div>
      </div>

      <div class="metric-card">
        <div class="metric-icon">
          <svg class="icon-svg" viewBox="0 0 24 24"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
        </div>
        <div class="metric-info">
          <div class="metric-label" data-i18n="privilegeLabel">Execution Mode</div>
          <div class="metric-val" id="telemetryPrivilege">Standard / PackageInstaller</div>
        </div>
      </div>

      <div class="metric-card">
        <div class="metric-icon">
          <svg class="icon-svg" viewBox="0 0 24 24"><polyline points="16 16 12 12 8 16"/><line x1="12" y1="12" x2="12" y2="21"/><path d="M20.39 18.39A5 5 0 0 0 18 9h-1.26A8 8 0 1 0 3 16.3"/></svg>
        </div>
        <div class="metric-info">
          <div class="metric-label" data-i18n="sessionStatusLabel">Active Session</div>
          <div class="metric-val" id="telemetrySession">Ready</div>
        </div>
      </div>
    </section>

    <!-- Main Workspace Tabs -->
    <nav class="nav-tabs">
      <button class="tab-btn active" onclick="switchTab('deploy', this)">
        <svg class="icon-svg" viewBox="0 0 24 24"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
        <span data-i18n="tabDeploy">Package Deploy & XAPK</span>
      </button>
      <button class="tab-btn" onclick="switchTab('apps', this)">
        <svg class="icon-svg" viewBox="0 0 24 24"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/></svg>
        <span data-i18n="tabApps">Installed Apps & Permissions</span>
      </button>
      <button class="tab-btn" onclick="switchTab('backups', this)">
        <svg class="icon-svg" viewBox="0 0 24 24"><polyline points="21 8 21 21 3 21 3 8"/><rect x="1" y="3" width="22" height="5"/><line x1="10" y1="12" x2="14" y2="12"/></svg>
        <span data-i18n="tabBackups">Backup & Restore Vault</span>
      </button>
      <button class="tab-btn" onclick="switchTab('store', this)">
        <svg class="icon-svg" viewBox="0 0 24 24"><path d="M6 2L3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z"/><line x1="3" y1="6" x2="21" y2="6"/><path d="M16 10a4 4 0 0 1-8 0"/></svg>
        <span data-i18n="tabStore">App Store & Repos</span>
      </button>
      <button class="tab-btn" onclick="switchTab('system', this)">
        <svg class="icon-svg" viewBox="0 0 24 24"><polyline points="4 17 10 11 4 5"/><line x1="12" y1="19" x2="20" y2="19"/></svg>
        <span data-i18n="tabSystem">System & Live Console</span>
      </button>
    </nav>

    <!-- TAB 1: PACKAGE DEPLOY & XAPK / SPLIT BUNDLE STUDIO -->
    <section id="panel-deploy" class="panel active">
      <div class="grid-2">
        <div>
          <div class="card">
            <div class="card-header">
              <div>
                <div class="card-title">
                  <svg class="icon-svg" style="color:var(--accent)" viewBox="0 0 24 24"><path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/></svg>
                  <span data-i18n="uploadCardTitle">Multi-Format Package & Split APK Uploader</span>
                </div>
                <div class="card-desc" data-i18n="uploadCardDesc">Drag and drop single or multiple packages. XAPK, APKS, APKM, OBB, and loose Split APKs are automatically resolved.</div>
              </div>
            </div>

            <!-- Upload Mode Toggle -->
            <div class="switch-row" style="margin-bottom:14px;">
              <div>
                <div style="font-size:13px;font-weight:700;" data-i18n="bundleModeTitle">Combine Multiple Selected APKs into 1 Split Session</div>
                <div style="font-size:11.5px;color:var(--text-muted);" data-i18n="bundleModeDesc">Enable when selecting base.apk + split_config.*.apk together</div>
              </div>
              <label class="toggle-switch">
                <input type="checkbox" id="combineSplitsToggle">
                <span class="toggle-slider"></span>
              </label>
            </div>

            <div class="switch-row" style="margin-bottom:16px;">
              <div>
                <div style="font-size:13px;font-weight:700;" data-i18n="autoInstallTitle">Automatically Start Installation on Device</div>
                <div style="font-size:11.5px;color:var(--text-muted);" data-i18n="autoInstallDesc">Immediately trigger PackageInstaller / Root session after transfer</div>
              </div>
              <label class="toggle-switch">
                <input type="checkbox" id="autoInstallToggle" checked>
                <span class="toggle-slider"></span>
              </label>
            </div>

            <!-- Dropzone -->
            <div class="dropzone" id="dropzone">
              <div class="dropzone-circle">
                <svg class="icon-svg" viewBox="0 0 24 24"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
              </div>
              <div style="font-size:15px;font-weight:800;color:var(--text-primary);" data-i18n="dropTitle">Drop APK / XAPK / APKS / APKM / ZIP / TAR.GZ here</div>
              <div style="font-size:12.5px;color:var(--text-muted);margin-top:4px;" data-i18n="dropSubtitle">Or click to browse files from your computer (Multi-select supported)</div>
              <div class="format-chips">
                <span class="fmt-chip">.apk</span>
                <span class="fmt-chip">.xapk</span>
                <span class="fmt-chip">.apks</span>
                <span class="fmt-chip">.apkm</span>
                <span class="fmt-chip">.aab</span>
                <span class="fmt-chip">.zip</span>
                <span class="fmt-chip">.tar.gz</span>
                <span class="fmt-chip">.tgz</span>
                <span class="fmt-chip">.apex</span>
                <span class="fmt-chip">.obb.zip</span>
              </div>
            </div>
            <input type="file" id="fileInput" multiple style="display:none">

            <!-- Active Upload Progress -->
            <div class="progress-wrap" id="uploadProgressWrap">
              <div style="display:flex;justify-content:space-between;font-size:12.5px;font-weight:700;">
                <span id="uploadFileName">Uploading...</span>
                <span id="uploadStats">0%</span>
              </div>
              <div class="progress-track">
                <div class="progress-bar" id="uploadProgressBar"></div>
              </div>
            </div>
          </div>

          <!-- Direct URL Installation Card -->
          <div class="card">
            <div class="card-header">
              <div>
                <div class="card-title">
                  <svg class="icon-svg" style="color:var(--accent)" viewBox="0 0 24 24"><path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"/><path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"/></svg>
                  <span data-i18n="directUrlTitle">Direct URL Package Fetch & Install</span>
                </div>
                <div class="card-desc" data-i18n="directUrlDesc">Send an HTTP/HTTPS direct link (.apk, .xapk, .apks) for the Android device to download and parse directly.</div>
              </div>
            </div>
            <div style="display:flex;gap:10px;flex-wrap:wrap;">
              <input type="text" id="directUrlInput" placeholder="https://example.com/release/app-release.xapk" style="flex:1;">
              <button class="btn" onclick="sendDirectUrl()">
                <svg class="icon-svg" viewBox="0 0 24 24"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
                <span data-i18n="fetchAndInstallBtn">Fetch on Device</span>
              </button>
            </div>
          </div>
        </div>

        <!-- Right Column: Pre-Install Custom Metadata Override & Live Device Install Status -->
        <div>
          <div class="card">
            <div class="card-header">
              <div>
                <div class="card-title">
                  <svg class="icon-svg" style="color:var(--accent)" viewBox="0 0 24 24"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
                  <span data-i18n="preOverrideTitle">Install-Time Metadata & Bundle Override</span>
                </div>
                <div class="card-desc" data-i18n="preOverrideDesc">Optionally customize the App Name, Bundle ID (Package Name), Version, or Genre during upload.</div>
              </div>
            </div>
            <div class="form-grid">
              <div class="field-group">
                <label class="field-label" data-i18n="fieldAppName">Custom App Name</label>
                <input type="text" id="ovAppLabel" placeholder="Leave blank for default">
              </div>
              <div class="field-group">
                <label class="field-label" data-i18n="fieldBundleId">Custom Bundle ID (Package Name)</label>
                <input type="text" id="ovPackageName" placeholder="com.custom.package">
              </div>
              <div class="field-group">
                <label class="field-label" data-i18n="fieldVersionName">Custom Version Name</label>
                <input type="text" id="ovVersionName" placeholder="e.g. 2.0.0-custom">
              </div>
              <div class="field-group">
                <label class="field-label" data-i18n="fieldVersionCode">Custom Build Number</label>
                <input type="number" id="ovVersionCode" placeholder="e.g. 100">
              </div>
              <div class="field-group">
                <label class="field-label" data-i18n="fieldCategory">Genre / Category</label>
                <select class="input-select" id="ovCategory">
                  <option value="">Default</option>
                  <option value="ユーティリティ">ユーティリティ (Utility)</option>
                  <option value="開発・ターミナル">開発・ターミナル (Dev / Terminal)</option>
                  <option value="ネットワーク・通信">ネットワーク・通信 (Network)</option>
                  <option value="メディア・動画">メディア・動画 (Media)</option>
                  <option value="システム・最適化">システム・最適化 (System)</option>
                  <option value="セキュリティ">セキュリティ (Security)</option>
                  <option value="ゲーム">ゲーム (Game)</option>
                </select>
              </div>
            </div>
          </div>

          <!-- Live Device Parsed Package & Install Session Card -->
          <div class="card">
            <div class="card-header">
              <div>
                <div class="card-title">
                  <svg class="icon-svg" style="color:var(--accent)" viewBox="0 0 24 24"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/></svg>
                  <span data-i18n="liveSessionTitle">Device Install Session Monitor</span>
                </div>
                <div class="card-desc" id="deviceSessionTask">Waiting for package...</div>
              </div>
            </div>
            <div class="progress-track" style="margin-bottom:14px;">
              <div class="progress-bar" id="deviceSessionBar" style="width:0%"></div>
            </div>
            <div id="deviceSessionActions" style="display:flex;gap:8px;flex-wrap:wrap;">
              <button class="btn btn-sm" onclick="triggerDeviceInstall()">
                <svg class="icon-svg" viewBox="0 0 24 24"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg>
                <span data-i18n="btnReinstall">Install / Reinstall</span>
              </button>
              <button class="btn btn-success btn-sm" onclick="launchCurrentParsedApp()">
                <svg class="icon-svg" viewBox="0 0 24 24"><polygon points="5 3 19 12 5 21 5 3"/></svg>
                <span data-i18n="btnOpenApp">Open</span>
              </button>
              <button class="btn btn-danger btn-sm" onclick="uninstallCurrentParsedApp()">
                <svg class="icon-svg" viewBox="0 0 24 24"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
                <span data-i18n="btnDeleteApp">Delete</span>
              </button>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- TAB 2: INSTALLED APPS, METADATA PATCHER & PERMISSION STUDIO -->
    <section id="panel-apps" class="panel">
      <div class="card">
        <div class="card-header">
          <div>
            <div class="card-title">
              <svg class="icon-svg" style="color:var(--accent)" viewBox="0 0 24 24"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/></svg>
              <span data-i18n="appsManagerTitle">Installed Applications, Metadata & Permission Studio</span>
            </div>
            <div class="card-desc" data-i18n="appsManagerDesc">Inspect real app icons, launch apps, extract APK/Split bundles, modify bundle names/versions, and toggle permissions remotely.</div>
          </div>
          <div style="display:flex;gap:8px;flex-wrap:wrap;">
            <button class="btn btn-secondary btn-sm" onclick="loadApps()">
              <svg class="icon-svg" viewBox="0 0 24 24"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg>
              <span data-i18n="btnRefresh">Refresh</span>
            </button>
          </div>
        </div>

        <div class="toolbar-row">
          <div class="search-box">
            <svg class="icon-svg" viewBox="0 0 24 24"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
            <input type="text" id="appSearchInput" oninput="renderFilteredApps()" placeholder="Search by app name, package ID, or genre...">
          </div>
          <select class="input-select" id="appFilterSelect" onchange="renderFilteredApps()" style="width:auto;">
            <option value="user">User Apps</option>
            <option value="all">All Apps</option>
            <option value="system">System Apps</option>
            <option value="splits">Split APKs Only</option>
          </select>
          <select class="input-select" id="appSortSelect" onchange="renderFilteredApps()" style="width:auto;">
            <option value="name">Sort: Name</option>
            <option value="size">Sort: Size</option>
            <option value="version">Sort: Version</option>
          </select>
        </div>

        <div id="appsContainer" class="app-grid"></div>
      </div>
    </section>

    <!-- TAB 3: BACKUP & RESTORE VAULT -->
    <section id="panel-backups" class="panel">
      <div class="card">
        <div class="card-header">
          <div>
            <div class="card-title">
              <svg class="icon-svg" style="color:var(--accent)" viewBox="0 0 24 24"><polyline points="21 8 21 21 3 21 3 8"/><rect x="1" y="3" width="22" height="5"/><line x1="10" y1="12" x2="14" y2="12"/></svg>
              <span data-i18n="backupsTitle">Device Backup Archives & Multi-Format Export</span>
            </div>
            <div class="card-desc" data-i18n="backupsDesc">Create backups in standard formats (.apks, .xapk, .apk, .tar.gz, .zip), download archives to PC, or trigger device restoration.</div>
          </div>
          <button class="btn btn-secondary btn-sm" onclick="loadBackups()">
            <svg class="icon-svg" viewBox="0 0 24 24"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg>
            <span data-i18n="btnRefresh">Refresh</span>
          </button>
        </div>

        <div style="display:flex;gap:10px;flex-wrap:wrap;margin-bottom:18px;background:var(--bg-elevated);padding:14px;border-radius:12px;">
          <input type="text" id="backupPkgInput" placeholder="Package name to backup (e.g. org.localsend.localsend_app)" style="flex:1;">
          <select class="input-select" id="backupFormatSelect" style="width:auto;">
            <option value="apks">.apks (Split Bundle)</option>
            <option value="xapk">.xapk (XAPK Standard)</option>
            <option value="apk">.apk (Single / Bundle)</option>
            <option value="apkm">.apkm (APKMirror)</option>
            <option value="tar.gz">.tar.gz (GZIP Tar)</option>
            <option value="tgz">.tgz (TGZ Archive)</option>
            <option value="tar">.tar (POSIX Tar)</option>
            <option value="zip">.zip (Standard ZIP)</option>
            <option value="apk.gz">.apk.gz (GZIP Stream)</option>
            <option value="aab">.aab (App Bundle)</option>
            <option value="apex">.apex (APEX Container)</option>
            <option value="jar">.jar (JAR Container)</option>
            <option value="ab">.ab (Android Backup)</option>
          </select>
          <button class="btn" onclick="createRemoteBackup()">
            <svg class="icon-svg" viewBox="0 0 24 24"><path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"/><polyline points="17 21 17 13 7 13 7 21"/></svg>
            <span data-i18n="btnCreateBackup">Create Backup</span>
          </button>
        </div>

        <div id="backupsContainer" class="app-grid"></div>
      </div>
    </section>

    <!-- TAB 4: APP STORE & REPO SYNC -->
    <section id="panel-store" class="panel">
      <div class="card">
        <div class="card-header">
          <div>
            <div class="card-title">
              <svg class="icon-svg" style="color:var(--accent)" viewBox="0 0 24 24"><path d="M6 2L3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z"/><line x1="3" y1="6" x2="21" y2="6"/><path d="M16 10a4 4 0 0 1-8 0"/></svg>
              <span data-i18n="storeTitle">App Store Catalog & GitHub / Custom Repository Sync</span>
            </div>
            <div class="card-desc" data-i18n="storeDesc">Remotely sync GitHub releases or custom JSON repositories and trigger direct installation onto the device.</div>
          </div>
          <button class="btn btn-sm" onclick="openModal('repoModal')">
            <svg class="icon-svg" viewBox="0 0 24 24"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
            <span data-i18n="btnAddRepos">Bulk Add Repositories</span>
          </button>
        </div>
        <div id="storeContainer" class="app-grid"></div>
      </div>
    </section>

    <!-- TAB 5: SYSTEM OPTIMIZER & LIVE CONSOLE -->
    <section id="panel-system" class="panel">
      <div class="grid-2">
        <div class="card">
          <div class="card-header">
            <div>
              <div class="card-title">
                <svg class="icon-svg" style="color:var(--accent)" viewBox="0 0 24 24"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/></svg>
                <span data-i18n="sysOptTitle">Remote System Maintenance & Optimization</span>
              </div>
              <div class="card-desc" data-i18n="sysOptDesc">Clean temporary package caches or trigger ART dex2oat speed compilation on the device.</div>
            </div>
          </div>
          <div style="display:flex;flex-direction:column;gap:12px;">
            <div class="switch-row">
              <div>
                <div style="font-size:14px;font-weight:700;" data-i18n="cleanCacheTitle">Purge Temporary APK & System Caches</div>
                <div style="font-size:12px;color:var(--text-muted);" data-i18n="cleanCacheDesc">Frees up extracted split staging directories and residual files</div>
              </div>
              <button class="btn btn-sm" onclick="cleanRemoteCache()">
                <svg class="icon-svg" viewBox="0 0 24 24"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6"/></svg>
                <span data-i18n="btnCleanCache">Clean Cache</span>
              </button>
            </div>
          </div>
        </div>

        <div class="card">
          <div class="card-header">
            <div class="card-title">
              <svg class="icon-svg" style="color:var(--accent)" viewBox="0 0 24 24"><polyline points="4 17 10 11 4 5"/><line x1="12" y1="19" x2="20" y2="19"/></svg>
              <span data-i18n="liveLogTitle">Real-Time Server & Telemetry Log</span>
            </div>
          </div>
          <div class="log-box" id="serverLogBox"></div>
        </div>
      </div>
    </section>
  </main>

  <!-- MODAL: EDIT APP METADATA & BUNDLE ID -->
  <div class="modal-backdrop" id="metaModal">
    <div class="modal-box">
      <div class="card-header">
        <div class="card-title">
          <svg class="icon-svg" style="color:var(--accent)" viewBox="0 0 24 24"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
          <span data-i18n="modalMetaTitle">Edit App Name, Bundle ID, Version & Genre</span>
        </div>
        <button class="icon-btn" onclick="closeModal('metaModal')">
          <svg class="icon-svg" viewBox="0 0 24 24"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
        </button>
      </div>
      <input type="hidden" id="editOrigPkg">
      <div class="form-grid" style="grid-template-columns:1fr;">
        <div class="field-group">
          <label class="field-label" data-i18n="fieldAppName">App Display Name</label>
          <input type="text" id="editAppLabel">
        </div>
        <div class="field-group">
          <label class="field-label" data-i18n="fieldBundleId">Bundle ID (Package Name)</label>
          <input type="text" id="editBundleId">
        </div>
        <div style="display:grid;grid-template-columns:1fr 1fr;gap:10px;">
          <div class="field-group">
            <label class="field-label" data-i18n="fieldVersionName">Version Name</label>
            <input type="text" id="editVersionName">
          </div>
          <div class="field-group">
            <label class="field-label" data-i18n="fieldVersionCode">Build Number</label>
            <input type="number" id="editVersionCode">
          </div>
        </div>
        <div class="field-group">
          <label class="field-label" data-i18n="fieldCategory">Genre / Category</label>
          <select class="input-select" id="editCategory">
            <option value="ユーティリティ">ユーティリティ (Utility)</option>
            <option value="開発・ターミナル">開発・ターミナル (Dev / Terminal)</option>
            <option value="ネットワーク・通信">ネットワーク・通信 (Network)</option>
            <option value="メディア・動画">メディア・動画 (Media)</option>
            <option value="システム・最適化">システム・最適化 (System)</option>
            <option value="セキュリティ">セキュリティ (Security)</option>
            <option value="ゲーム">ゲーム (Game)</option>
          </select>
        </div>
      </div>
      <div style="display:flex;justify-content:flex-end;gap:10px;margin-top:20px;flex-wrap:wrap;">
        <button class="btn btn-secondary" onclick="closeModal('metaModal')" data-i18n="btnCancel">Cancel</button>
        <button class="btn btn-secondary" onclick="saveAppMetadata(false)" data-i18n="btnSaveMeta">Save Metadata</button>
        <button class="btn" onclick="saveAppMetadata(true)" data-i18n="btnPatchReinstall">Patch APK & Reinstall</button>
      </div>
    </div>
  </div>

  <!-- MODAL: INTERACTIVE PERMISSIONS EDITOR -->
  <div class="modal-backdrop" id="permModal">
    <div class="modal-box">
      <div class="card-header">
        <div>
          <div class="card-title">
            <svg class="icon-svg" style="color:var(--accent)" viewBox="0 0 24 24"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
            <span id="permModalTitle">App Permissions</span>
          </div>
          <div class="card-desc" id="permModalSub"></div>
        </div>
        <button class="icon-btn" onclick="closeModal('permModal')">
          <svg class="icon-svg" viewBox="0 0 24 24"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
        </button>
      </div>
      <div id="permListContainer" style="display:flex;flex-direction:column;gap:8px;margin-top:12px;"></div>
    </div>
  </div>

  <!-- MODAL: BULK ADD REPOSITORIES -->
  <div class="modal-backdrop" id="repoModal">
    <div class="modal-box">
      <div class="card-header">
        <div class="card-title">
          <svg class="icon-svg" style="color:var(--accent)" viewBox="0 0 24 24"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
          <span data-i18n="btnAddRepos">Bulk Add Repositories</span>
        </div>
        <button class="icon-btn" onclick="closeModal('repoModal')">
          <svg class="icon-svg" viewBox="0 0 24 24"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
        </button>
      </div>
      <p style="font-size:12.5px;color:var(--text-muted);margin-bottom:10px;">Enter GitHub repositories (owner/repo) or custom JSON index URLs separated by newlines or commas.</p>
      <textarea class="input-area" id="bulkRepoTextarea" rows="5" placeholder="localsend/localsend&#10;termux/termux-app"></textarea>
      <div style="display:flex;justify-content:flex-end;gap:10px;margin-top:16px;">
        <button class="btn btn-secondary" onclick="closeModal('repoModal')" data-i18n="btnCancel">Cancel</button>
        <button class="btn" onclick="submitBulkRepos()" data-i18n="btnSyncRepos">Sync Repositories</button>
      </div>
    </div>
  </div>

  <!-- Toast Stack -->
  <div class="toast-stack" id="toastStack"></div>

  <script>
    // --- Multi-Language i18n Dictionary ---
    const I18N = {
      ja: {
        appTitle: "OmniInstaller 遠隔操作スタジオ",
        themeLight: "ライト",
        themeDark: "ダーク",
        themeAuto: "自動",
        storageLabel: "内部ストレージ空き容量",
        memoryLabel: "システムメモリ (RAM)",
        privilegeLabel: "動作モード・権限",
        sessionStatusLabel: "インストールセッション状態",
        tabDeploy: "パッケージ・XAPK導入",
        tabApps: "アプリ・情報・権限管理",
        tabBackups: "バックアップ・復元",
        tabStore: "ストア・リポジトリ同期",
        tabSystem: "システム診断・ログ",
        uploadCardTitle: "マルチフォーマット＆分割APK アップローダー",
        uploadCardDesc: "単体・複数のパッケージをドラッグ＆ドロップ。XAPK / APKS / APKM / OBB / 複数Split APKを自動解析します。",
        bundleModeTitle: "選択した複数のAPKを1つのSplitバンドルとして統合導入",
        bundleModeDesc: "base.apk と split_config.*.apk を同時に選択して一括導入する場合にONにします",
        autoInstallTitle: "転送完了後に端末で自動的にインストールを開始",
        autoInstallDesc: "アップロード完了と同時にPackageInstallerまたはRootセッションを起動します",
        dropTitle: "APK / XAPK / APKS / APKM / ZIP / TAR.GZ をここにドロップ",
        dropSubtitle: "またはクリックしてPC内のファイルを選択（複数ファイルの同時選択に対応）",
        directUrlTitle: "直リンクURLから直接ダウンロード＆導入",
        directUrlDesc: "HTTP/HTTPS の直リンク (.apk, .xapk, .apks 等) を端末へ送り、端末側で直接取得・解析します。",
        fetchAndInstallBtn: "端末で取得・解析",
        preOverrideTitle: "インストール時カスタム変更 (名前・バンドル名・バージョン・ジャンル)",
        preOverrideDesc: "導入時にアプリ表示名、バンドル名 (Package ID)、バージョン、ジャンルを変更して適用できます。",
        fieldAppName: "アプリ表示名",
        fieldBundleId: "バンドル名 (Package Name)",
        fieldVersionName: "バージョン名",
        fieldVersionCode: "ビルド番号",
        fieldCategory: "ジャンル・カテゴリ",
        liveSessionTitle: "端末インストールセッション監視",
        btnReinstall: "再インストール",
        btnOpenApp: "開く",
        btnDeleteApp: "削除",
        appsManagerTitle: "インストール済みアプリ・メタ情報・権限スタジオ",
        appsManagerDesc: "アプリアイコン表示、起動、APK抽出、バンドル名/バージョン変更、権限の付与/剥奪を遠隔操作できます。",
        btnRefresh: "更新",
        backupsTitle: "バックアップアーカイブ＆マルチ形式書き出し",
        backupsDesc: "標準形式 (.apks, .xapk, .apk, .tar.gz, .zip 等) でのバックアップ作成・PCへの保存・端末復元を行います。",
        btnCreateBackup: "バックアップ作成",
        storeTitle: "アプリストア＆リポジトリ同期",
        storeDesc: "LocalSend・Termux および追加リポジトリの確認と遠隔インストールを実行します。",
        btnAddRepos: "リポジトリ一括追加",
        sysOptTitle: "システムメンテナンス＆キャッシュ整理",
        sysOptDesc: "一時展開領域や残存APKキャッシュをクリーンアップします。",
        cleanCacheTitle: "一時ファイル＆キャッシュの一括消去",
        cleanCacheDesc: "展開済みの一時データや不要キャッシュを解放します",
        btnCleanCache: "クリーンアップ実行",
        liveLogTitle: "リアルタイム通信・導入ログ",
        modalMetaTitle: "アプリ名・バンドル名・バージョン・ジャンル変更",
        btnCancel: "キャンセル",
        btnSaveMeta: "変更を保存",
        btnPatchReinstall: "APK書き換え＆再インストール",
        btnSyncRepos: "同期・追加"
      },
      en: {
        appTitle: "OmniInstaller Remote Studio",
        themeLight: "Light",
        themeDark: "Dark",
        themeAuto: "Auto",
        storageLabel: "Device Storage",
        memoryLabel: "System Memory (RAM)",
        privilegeLabel: "Execution Mode",
        sessionStatusLabel: "Install Session Status",
        tabDeploy: "Package & XAPK Deploy",
        tabApps: "Apps & Permissions",
        tabBackups: "Backup & Restore",
        tabStore: "App Store & Repos",
        tabSystem: "System & Live Logs",
        uploadCardTitle: "Multi-Format Package & Split APK Uploader",
        uploadCardDesc: "Drag and drop single or multiple packages. XAPK, APKS, APKM, OBB, and Split APKs are automatically resolved.",
        bundleModeTitle: "Combine Multiple Selected APKs into 1 Split Session",
        bundleModeDesc: "Enable when selecting base.apk + split_config.*.apk files together",
        autoInstallTitle: "Automatically Start Installation on Device",
        autoInstallDesc: "Immediately trigger PackageInstaller or Root session after upload",
        dropTitle: "Drop APK / XAPK / APKS / APKM / ZIP / TAR.GZ here",
        dropSubtitle: "Or click to browse files from your computer (Multi-file selection supported)",
        directUrlTitle: "Direct URL Package Fetch & Install",
        directUrlDesc: "Send an HTTP/HTTPS direct link (.apk, .xapk, .apks) for the device to download and parse directly.",
        fetchAndInstallBtn: "Fetch on Device",
        preOverrideTitle: "Install-Time Metadata & Bundle Override",
        preOverrideDesc: "Customize the App Name, Bundle ID (Package Name), Version, or Genre during installation.",
        fieldAppName: "App Display Name",
        fieldBundleId: "Bundle ID (Package Name)",
        fieldVersionName: "Version Name",
        fieldVersionCode: "Build Number",
        fieldCategory: "Genre / Category",
        liveSessionTitle: "Device Install Session Monitor",
        btnReinstall: "Reinstall",
        btnOpenApp: "Open",
        btnDeleteApp: "Delete",
        appsManagerTitle: "Installed Applications, Metadata & Permission Studio",
        appsManagerDesc: "View app icons, launch apps, export bundles, modify bundle IDs/versions, and edit permissions remotely.",
        btnRefresh: "Refresh",
        backupsTitle: "Device Backup Archives & Multi-Format Export",
        backupsDesc: "Create backups in standard formats (.apks, .xapk, .apk, .tar.gz, .zip), download to PC, or restore.",
        btnCreateBackup: "Create Backup",
        storeTitle: "App Store Catalog & Repository Sync",
        storeDesc: "Browse LocalSend, Termux, and synced repositories or trigger remote installation.",
        btnAddRepos: "Bulk Add Repositories",
        sysOptTitle: "Remote System Maintenance & Optimization",
        sysOptDesc: "Clean temporary staging directories and residual package caches.",
        cleanCacheTitle: "Purge Temporary APK & System Caches",
        cleanCacheDesc: "Frees up extracted split staging directories and cache files",
        btnCleanCache: "Clean Cache",
        liveLogTitle: "Real-Time Server & Telemetry Log",
        modalMetaTitle: "Edit App Name, Bundle ID, Version & Genre",
        btnCancel: "Cancel",
        btnSaveMeta: "Save Metadata",
        btnPatchReinstall: "Patch APK & Reinstall",
        btnSyncRepos: "Sync Repositories"
      },
      zh: {
        appTitle: "OmniInstaller 远程控制中心",
        themeLight: "浅色",
        themeDark: "深色",
        themeAuto: "自动",
        storageLabel: "设备存储空间",
        memoryLabel: "系统内存 (RAM)",
        privilegeLabel: "运行模式",
        sessionStatusLabel: "安装会话状态",
        tabDeploy: "安装包与 XAPK 部署",
        tabApps: "应用与权限管理",
        tabBackups: "备份与恢复",
        tabStore: "应用商店与仓库",
        tabSystem: "系统诊断与日志",
        uploadCardTitle: "多格式安装包与 Split APK 上传器",
        uploadCardDesc: "拖放单个或多个安装包，自动解析 XAPK、APKS、APKM、OBB 及拆分 APK。",
        bundleModeTitle: "将所选多个 APK 合并为单个 Split 会话安装",
        bundleModeDesc: "同时选择 base.apk 和 split_config.*.apk 时开启",
        autoInstallTitle: "上传完成后在设备上自动开始安装",
        autoInstallDesc: "传输完成后立即调用系统安装器或 Root 静默安装",
        dropTitle: "拖放 APK / XAPK / APKS / APKM / ZIP / TAR.GZ 到此处",
        dropSubtitle: "或点击选择电脑上的文件（支持多选）",
        directUrlTitle: "直链 URL 远程下载安装",
        directUrlDesc: "发送 HTTP/HTTPS 直链由设备直接下载并解析安装。",
        fetchAndInstallBtn: "在设备上获取",
        preOverrideTitle: "安装时自定义名称、包名、版本与分类",
        preOverrideDesc: "在安装前自定义应用名称、包名 (Bundle ID)、版本号或分类。",
        fieldAppName: "应用名称",
        fieldBundleId: "包名 (Bundle ID)",
        fieldVersionName: "版本名称",
        fieldVersionCode: "构建号",
        fieldCategory: "应用分类",
        liveSessionTitle: "设备安装进度监控",
        btnReinstall: "重新安装",
        btnOpenApp: "打开",
        btnDeleteApp: "卸载",
        appsManagerTitle: "已安装应用、包名修改与权限管理",
        appsManagerDesc: "查看应用图标、远程启动、提取安装包、修改包名/版本及切换权限。",
        btnRefresh: "刷新",
        backupsTitle: "设备备份归档与多格式导出",
        backupsDesc: "支持导出为 .apks, .xapk, .apk, .tar.gz 等标准格式并下载到电脑或恢复。",
        btnCreateBackup: "创建备份",
        storeTitle: "应用商店与仓库同步",
        storeDesc: "管理 LocalSend、Termux 及自定义 GitHub 仓库。",
        btnAddRepos: "批量添加仓库",
        sysOptTitle: "远程系统维护与缓存清理",
        sysOptDesc: "清理临时解压目录与残留安装包缓存。",
        cleanCacheTitle: "清理临时文件与系统缓存",
        cleanCacheDesc: "释放拆包缓存与临时文件空间",
        btnCleanCache: "立即清理",
        liveLogTitle: "实时连接与安装日志",
        modalMetaTitle: "修改应用名称、包名、版本与分类",
        btnCancel: "取消",
        btnSaveMeta: "保存信息",
        btnPatchReinstall: "重写 APK 并重装",
        btnSyncRepos: "同步添加"
      },
      ko: {
        appTitle: "OmniInstaller 원격 스튜디오",
        themeLight: "라이트",
        themeDark: "다크",
        themeAuto: "자동",
        storageLabel: "기기 저장공간",
        memoryLabel: "시스템 메모리 (RAM)",
        privilegeLabel: "실행 모드",
        sessionStatusLabel: "설치 세션 상태",
        tabDeploy: "패키지 및 XAPK 배포",
        tabApps: "앱 및 권한 관리",
        tabBackups: "백업 및 복원",
        tabStore: "앱 스토어 및 저장소",
        tabSystem: "시스템 및 실시간 로그",
        uploadCardTitle: "멀티 포맷 패키지 및 Split APK 업로더",
        uploadCardDesc: "XAPK, APKS, APKM, OBB 및 분할 APK 파일을 드래그 앤 드롭하여 설치합니다.",
        bundleModeTitle: "선택한 여러 APK를 하나의 Split 세션으로 통합",
        bundleModeDesc: "base.apk와 split_config.*.apk를 함께 선택할 때 활성화하세요",
        autoInstallTitle: "전송 후 기기에서 자동으로 설치 시작",
        autoInstallDesc: "업로드 즉시 PackageInstaller 또는 Root 설치를 실행합니다",
        dropTitle: "APK / XAPK / APKS / APKM / ZIP / TAR.GZ 파일을 여기에 드롭",
        dropSubtitle: "또는 클릭하여 PC의 파일을 선택하세요 (다중 선택 지원)",
        directUrlTitle: "직접 링크(URL) 원격 다운로드 및 설치",
        directUrlDesc: "HTTP/HTTPS 직접 링크를 기기로 전송하여 즉시 다운로드 및 분석합니다.",
        fetchAndInstallBtn: "기기에서 가져오기",
        preOverrideTitle: "설치 시 이름·패키지명·버전·장르 변경",
        preOverrideDesc: "설치 시 앱 이름, 번들 ID, 버전, 카테고리를 사용자 지정합니다.",
        fieldAppName: "앱 표시 이름",
        fieldBundleId: "번들 ID (패키지명)",
        fieldVersionName: "버전 이름",
        fieldVersionCode: "빌드 번호",
        fieldCategory: "장르 / 카테고리",
        liveSessionTitle: "기기 설치 세션 모니터",
        btnReinstall: "재설치",
        btnOpenApp: "열기",
        btnDeleteApp: "삭제",
        appsManagerTitle: "설치된 앱·메타데이터·권한 관리",
        appsManagerDesc: "앱 아이콘 확인, 실행, APK 추출, 번들명/버전 변경 및 권한 허용/거부를 제어합니다.",
        btnRefresh: "새로고침",
        backupsTitle: "백업 아카이브 및 다중 포맷 내보내기",
        backupsDesc: "표준 포맷(.apks, .xapk, .apk, .tar.gz)으로 백업 생성, PC 다운로드 및 복원을 수행합니다.",
        btnCreateBackup: "백업 생성",
        storeTitle: "앱 스토어 및 저장소 동기화",
        storeDesc: "LocalSend, Termux 및 추가된 저장소 앱을 원격 설치합니다.",
        btnAddRepos: "저장소 일괄 추가",
        sysOptTitle: "원격 시스템 최적화 및 캐시 정리",
        sysOptDesc: "임시 패키지 파일 및 캐시를 정리합니다.",
        cleanCacheTitle: "임시 파일 및 캐시 삭제",
        cleanCacheDesc: "임시 추출 폴더와 잔여 APK 파일을 정리합니다",
        btnCleanCache: "캐시 정리",
        liveLogTitle: "실시간 서버 및 설치 로그",
        modalMetaTitle: "앱 이름·번들 ID·버전·장르 편집",
        btnCancel: "취소",
        btnSaveMeta: "변경 저장",
        btnPatchReinstall: "APK 패치 및 재설치",
        btnSyncRepos: "동기화"
      }
    };

    let currentLang = 'ja';
    let cachedApps = [];
    let currentParsedPkg = '';

    function resolveLanguage(mode) {
      if (mode && mode !== 'auto' && I18N[mode]) return mode;
      const nav = (navigator.language || 'en').toLowerCase();
      if (nav.startsWith('ja')) return 'ja';
      if (nav.startsWith('zh')) return 'zh';
      if (nav.startsWith('ko')) return 'ko';
      return 'en';
    }

    function changeLanguage(mode) {
      localStorage.setItem('omni_remote_lang', mode);
      currentLang = resolveLanguage(mode);
      const dict = I18N[currentLang] || I18N.en;
      document.querySelectorAll('[data-i18n]').forEach(el => {
        const key = el.getAttribute('data-i18n');
        if (dict[key]) el.textContent = dict[key];
      });
    }

    // --- Light-based Theme with Dark & System Auto Support ---
    function setThemeMode(mode) {
      localStorage.setItem('omni_remote_theme', mode);
      document.getElementById('themeLightBtn').classList.toggle('active', mode === 'light');
      document.getElementById('themeDarkBtn').classList.toggle('active', mode === 'dark');
      document.getElementById('themeAutoBtn').classList.toggle('active', mode === 'auto');

      let effective = mode;
      if (mode === 'auto') {
        effective = window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
      }
      document.documentElement.setAttribute('data-theme', effective);
    }

    window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
      if (localStorage.getItem('omni_remote_theme') === 'auto') {
        setThemeMode('auto');
      }
    });

    function showToast(msg, type = 'info') {
      const stack = document.getElementById('toastStack');
      const item = document.createElement('div');
      item.className = 'toast-item';
      if (type === 'error') item.style.borderLeftColor = 'var(--danger)';
      if (type === 'success') item.style.borderLeftColor = 'var(--success)';
      item.innerHTML = '<svg class="icon-svg" viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg><span></span>';
      item.querySelector('span').textContent = msg;
      stack.appendChild(item);
      setTimeout(() => { item.remove(); }, 4200);
    }

    function switchTab(name, btn) {
      document.querySelectorAll('.panel').forEach(p => p.classList.remove('active'));
      document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
      document.getElementById('panel-' + name).classList.add('active');
      if (btn) btn.classList.add('active');
      if (name === 'apps') loadApps();
      if (name === 'backups') loadBackups();
      if (name === 'store') loadStore();
      if (name === 'system') pollTelemetry();
    }

    function openModal(id) {
      document.getElementById(id).classList.add('open');
    }
    function closeModal(id) {
      document.getElementById(id).classList.remove('open');
    }

    // --- File Upload & Multi-APK Bundle Studio ---
    const dropzone = document.getElementById('dropzone');
    const fileInput = document.getElementById('fileInput');
    dropzone.addEventListener('click', () => fileInput.click());
    dropzone.addEventListener('dragover', e => { e.preventDefault(); dropzone.classList.add('dragover'); });
    dropzone.addEventListener('dragleave', () => dropzone.classList.remove('dragover'));
    dropzone.addEventListener('drop', e => {
      e.preventDefault();
      dropzone.classList.remove('dragover');
      if (e.dataTransfer.files.length > 0) handleSelectedFiles(Array.from(e.dataTransfer.files));
    });
    fileInput.addEventListener('change', () => {
      if (fileInput.files.length > 0) handleSelectedFiles(Array.from(fileInput.files));
    });

    async function handleSelectedFiles(files) {
      const combineSplits = document.getElementById('combineSplitsToggle').checked;
      const autoInstall = document.getElementById('autoInstallToggle').checked;
      if (files.length > 1 && combineSplits) {
        const batchId = 'batch_' + Date.now();
        for (let i = 0; i < files.length; i++) {
          const isLast = (i === files.length - 1);
          await uploadSingleFile(files[i], batchId, isLast, autoInstall);
        }
      } else {
        for (let i = 0; i < files.length; i++) {
          await uploadSingleFile(files[i], '', false, autoInstall);
        }
      }
    }

    function uploadSingleFile(file, batchId, finalizeBatch, autoInstall) {
      return new Promise((resolve) => {
        const wrap = document.getElementById('uploadProgressWrap');
        const bar = document.getElementById('uploadProgressBar');
        const nameEl = document.getElementById('uploadFileName');
        const statsEl = document.getElementById('uploadStats');
        wrap.classList.add('active');
        nameEl.textContent = file.name;
        bar.style.width = '0%';

        const startTime = Date.now();
        const xhr = new XMLHttpRequest();
        const endpoint = batchId ? ('/api/upload-bundle?batchId=' + encodeURIComponent(batchId) + '&finalize=' + (finalizeBatch ? '1' : '0') + '&autoInstall=' + (autoInstall ? '1' : '0')) : '/upload';
        xhr.open('POST', endpoint, true);
        xhr.setRequestHeader('X-Filename', encodeURIComponent(file.name));
        xhr.setRequestHeader('X-Auto-Install', autoInstall ? '1' : '0');

        const ovLabel = document.getElementById('ovAppLabel').value.trim();
        const ovPkg = document.getElementById('ovPackageName').value.trim();
        const ovVer = document.getElementById('ovVersionName').value.trim();
        const ovCode = document.getElementById('ovVersionCode').value.trim();
        const ovCat = document.getElementById('ovCategory').value.trim();
        if (ovLabel) xhr.setRequestHeader('X-Custom-Label', encodeURIComponent(ovLabel));
        if (ovPkg) xhr.setRequestHeader('X-Custom-Pkg', encodeURIComponent(ovPkg));
        if (ovVer) xhr.setRequestHeader('X-Custom-VerName', encodeURIComponent(ovVer));
        if (ovCode) xhr.setRequestHeader('X-Custom-VerCode', ovCode);
        if (ovCat) xhr.setRequestHeader('X-Custom-Category', encodeURIComponent(ovCat));
        xhr.setRequestHeader('Content-Type', 'application/octet-stream');

        xhr.upload.onprogress = (e) => {
          if (e.lengthComputable) {
            const pct = Math.round((e.loaded / e.total) * 100);
            const elapsedSec = Math.max(0.1, (Date.now() - startTime) / 1000);
            const speedMB = ((e.loaded / 1048576) / elapsedSec).toFixed(1);
            bar.style.width = pct + '%';
            statsEl.textContent = pct + '% • ' + speedMB + ' MB/s';
          }
        };

        xhr.onload = () => {
          if (xhr.status === 200) {
            showToast(file.name + ' transferred to device', 'success');
            pollTelemetry();
          } else {
            showToast('Upload failed: ' + file.name, 'error');
          }
          resolve();
        };
        xhr.onerror = () => {
          showToast('Network error during upload', 'error');
          resolve();
        };
        xhr.send(file);
      });
    }

    function sendDirectUrl() {
      const url = document.getElementById('directUrlInput').value.trim();
      if (!url) return;
      fetch('/install-url', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({url})
      }).then(r => r.json()).then(d => {
        showToast(d.message || 'Direct URL sent to device', d.status === 'ok' ? 'success' : 'error');
      });
    }

    function triggerDeviceInstall() {
      fetch('/api/session/install', {method: 'POST'}).then(r => r.json()).then(d => {
        showToast(d.message || 'Install triggered on device', 'success');
      });
    }

    function launchCurrentParsedApp() {
      if (!currentParsedPkg) return;
      launchApp(currentParsedPkg);
    }

    function uninstallCurrentParsedApp() {
      if (!currentParsedPkg) return;
      uninstallApp(currentParsedPkg);
    }

    // --- Installed Apps Studio ---
    function loadApps() {
      const box = document.getElementById('appsContainer');
      box.innerHTML = '<div style="padding:20px;color:var(--text-muted);">Loading installed applications...</div>';
      fetch('/api/apps').then(r => r.json()).then(data => {
        if (data.status !== 'ok') {
          box.innerHTML = '<div style="padding:20px;color:var(--danger);font-weight:700;">' + (data.message || 'Remote App Management permission is disabled on the device.') + '</div>';
          return;
        }
        cachedApps = data.apps || [];
        renderFilteredApps();
      });
    }

    function renderFilteredApps() {
      const box = document.getElementById('appsContainer');
      const q = (document.getElementById('appSearchInput').value || '').toLowerCase();
      const filter = document.getElementById('appFilterSelect').value;
      const sort = document.getElementById('appSortSelect').value;

      let list = cachedApps.filter(app => {
        if (filter === 'user' && app.isSystemApp) return false;
        if (filter === 'system' && !app.isSystemApp) return false;
        if (filter === 'splits' && !app.isSplitApk) return false;
        if (q) {
          const match = (app.appLabel || '').toLowerCase().includes(q) ||
            (app.packageName || '').toLowerCase().includes(q) ||
            (app.customPackageName || '').toLowerCase().includes(q) ||
            (app.category || '').toLowerCase().includes(q);
          if (!match) return false;
        }
        return true;
      });

      list.sort((a, b) => {
        if (sort === 'size') return (b.sizeMB || 0) - (a.sizeMB || 0);
        if (sort === 'version') return (b.versionCode || 0) - (a.versionCode || 0);
        return (a.appLabel || '').localeCompare(b.appLabel || '');
      });

      box.innerHTML = '';
      list.forEach(app => {
        const card = document.createElement('div');
        card.className = 'app-card';
        const iconUrl = '/api/apps/icon?pkg=' + encodeURIComponent(app.packageName);
        const splitBadge = app.isSplitApk ? '<span class="tag-badge">Split APK</span>' : '';
        card.innerHTML = `
          <div class="app-left">
            <img class="app-icon" src="${'$'}{iconUrl}" alt="" loading="lazy">
            <div class="app-meta">
              <div class="app-name-row">
                <span class="app-name"></span>
                <span class="tag-badge">${'$'}{app.category || 'App'}</span>
                ${'$'}{splitBadge}
              </div>
              <div class="app-pkg"></div>
            </div>
          </div>
          <div class="app-actions">
            <button class="btn btn-success btn-sm" title="Open on Device" onclick="launchApp('${'$'}{app.packageName}')">
              <svg class="icon-svg" viewBox="0 0 24 24"><polygon points="5 3 19 12 5 21 5 3"/></svg>
              <span>Open</span>
            </button>
            <a class="btn btn-secondary btn-sm" href="/api/apps/export?pkg=${'$'}{encodeURIComponent(app.packageName)}" title="Download APK">
              <svg class="icon-svg" viewBox="0 0 24 24"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
              <span>APK</span>
            </a>
            <button class="btn btn-secondary btn-sm" data-edit="1">
              <svg class="icon-svg" viewBox="0 0 24 24"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
              <span>Edit Info</span>
            </button>
            <button class="btn btn-secondary btn-sm" data-perm="1">
              <svg class="icon-svg" viewBox="0 0 24 24"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
              <span>Permissions (${'$'}{(app.permissions || []).length})</span>
            </button>
            <button class="btn btn-danger btn-sm" onclick="uninstallApp('${'$'}{app.packageName}')">
              <svg class="icon-svg" viewBox="0 0 24 24"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6"/></svg>
            </button>
          </div>
        `;
        card.querySelector('.app-name').textContent = app.appLabel;
        card.querySelector('.app-pkg').textContent = app.customPackageName + ' • v' + app.versionName + ' (' + app.versionCode + ') • ' + app.sizeMB + ' MB';
        card.querySelector('[data-edit]').onclick = () => openEditMetaModal(app);
        card.querySelector('[data-perm]').onclick = () => openPermModal(app);
        box.appendChild(card);
      });
    }

    function launchApp(pkg) {
      fetch('/api/apps/launch', {method:'POST', body:JSON.stringify({packageName:pkg})})
        .then(r => r.json()).then(d => showToast(d.message, d.status === 'ok' ? 'success' : 'error'));
    }

    function uninstallApp(pkg) {
      fetch('/api/apps/uninstall', {method:'POST', body:JSON.stringify({packageName:pkg})})
        .then(r => r.json()).then(d => {
          showToast(d.message, d.status === 'ok' ? 'success' : 'error');
          setTimeout(loadApps, 800);
        });
    }

    function openEditMetaModal(app) {
      document.getElementById('editOrigPkg').value = app.packageName;
      document.getElementById('editAppLabel').value = app.appLabel || '';
      document.getElementById('editBundleId').value = app.customPackageName || app.packageName;
      document.getElementById('editVersionName').value = app.versionName || '1.0';
      document.getElementById('editVersionCode').value = app.versionCode || 1;
      document.getElementById('editCategory').value = app.category || 'ユーティリティ';
      openModal('metaModal');
    }

    function saveAppMetadata(reinstallModified) {
      const payload = {
        originalPackageName: document.getElementById('editOrigPkg').value,
        customAppLabel: document.getElementById('editAppLabel').value.trim(),
        customPackageName: document.getElementById('editBundleId').value.trim(),
        customVersionName: document.getElementById('editVersionName').value.trim(),
        customVersionCode: parseInt(document.getElementById('editVersionCode').value || '1', 10),
        customCategory: document.getElementById('editCategory').value,
        reinstallModified: reinstallModified
      };
      fetch('/api/apps/metadata', {method:'POST', body:JSON.stringify(payload)})
        .then(r => r.json()).then(d => {
          closeModal('metaModal');
          showToast(d.message, d.status === 'ok' ? 'success' : 'error');
          loadApps();
        });
    }

    function openPermModal(app) {
      document.getElementById('permModalTitle').textContent = app.appLabel + ' — Permissions';
      document.getElementById('permModalSub').textContent = app.packageName;
      const box = document.getElementById('permListContainer');
      box.innerHTML = '';
      const perms = app.permissions || [];
      if (perms.length === 0) {
        box.innerHTML = '<div style="color:var(--text-muted);font-size:13px;">No permissions declared.</div>';
      } else {
        perms.forEach(p => {
          const row = document.createElement('div');
          row.className = 'switch-row';
          row.innerHTML = `
            <div style="min-width:0;flex:1;">
              <div style="font-size:12.5px;font-weight:700;font-family:monospace;word-break:break-all;">${'$'}{p.name}</div>
              <div style="font-size:11px;color:var(--text-muted);">${'$'}{p.group}</div>
            </div>
            <label class="toggle-switch">
              <input type="checkbox" ${'$'}{p.granted ? 'checked' : ''}>
              <span class="toggle-slider"></span>
            </label>
          `;
          row.querySelector('input').onchange = (e) => {
            const grant = e.target.checked;
            fetch('/api/apps/permission', {
              method: 'POST',
              body: JSON.stringify({packageName: app.packageName, permission: p.fullName, grant: grant})
            }).then(r => r.json()).then(d => {
              showToast(d.message, d.status === 'ok' ? 'success' : 'error');
              p.granted = grant;
            });
          };
          box.appendChild(row);
        });
      }
      openModal('permModal');
    }

    // --- Backup & Restore Vault ---
    function loadBackups() {
      const box = document.getElementById('backupsContainer');
      fetch('/api/backups').then(r => r.json()).then(data => {
        box.innerHTML = '';
        const list = data.backups || [];
        if (list.length === 0) {
          box.innerHTML = '<div style="padding:18px;color:var(--text-muted);">No backup archives stored yet.</div>';
          return;
        }
        list.forEach(b => {
          const card = document.createElement('div');
          card.className = 'app-card';
          card.innerHTML = `
            <div class="app-left">
              <div class="metric-icon">
                <svg class="icon-svg" viewBox="0 0 24 24"><polyline points="21 8 21 21 3 21 3 8"/><rect x="1" y="3" width="22" height="5"/></svg>
              </div>
              <div class="app-meta">
                <div class="app-name-row">
                  <span class="app-name">${'$'}{b.appLabel}</span>
                  <span class="tag-badge">.${'$'}{b.format}</span>
                </div>
                <div class="app-pkg">${'$'}{b.packageName} • v${'$'}{b.versionName} • ${'$'}{b.sizeMB} MB</div>
              </div>
            </div>
            <div class="app-actions">
              <a class="btn btn-secondary btn-sm" href="/api/backups/download?id=${'$'}{encodeURIComponent(b.id)}">
                <svg class="icon-svg" viewBox="0 0 24 24"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
                <span>Download</span>
              </a>
              <button class="btn btn-sm" onclick="restoreRemoteBackup('${'$'}{b.id}')">
                <svg class="icon-svg" viewBox="0 0 24 24"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg>
                <span>Restore</span>
              </button>
              <button class="btn btn-danger btn-sm" onclick="deleteRemoteBackup('${'$'}{b.id}')">
                <svg class="icon-svg" viewBox="0 0 24 24"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6"/></svg>
              </button>
            </div>
          `;
          box.appendChild(card);
        });
      });
    }

    function createRemoteBackup() {
      const pkg = document.getElementById('backupPkgInput').value.trim();
      const fmt = document.getElementById('backupFormatSelect').value;
      if (!pkg) return;
      showToast('Creating .' + fmt + ' backup for ' + pkg + '...', 'info');
      fetch('/api/backups/create', {method:'POST', body:JSON.stringify({packageName:pkg, format:fmt})})
        .then(r => r.json()).then(d => {
          showToast(d.message, d.status === 'ok' ? 'success' : 'error');
          loadBackups();
        });
    }

    function restoreRemoteBackup(id) {
      fetch('/api/backups/restore', {method:'POST', body:JSON.stringify({id})})
        .then(r => r.json()).then(d => showToast(d.message, d.status === 'ok' ? 'success' : 'error'));
    }

    function deleteRemoteBackup(id) {
      fetch('/api/backups/delete', {method:'POST', body:JSON.stringify({id})})
        .then(r => r.json()).then(d => {
          showToast(d.message, 'info');
          loadBackups();
        });
    }

    // --- Store & Repository Sync ---
    function loadStore() {
      const box = document.getElementById('storeContainer');
      fetch('/api/store').then(r => r.json()).then(data => {
        box.innerHTML = '';
        (data.apps || []).forEach(app => {
          const card = document.createElement('div');
          card.className = 'app-card';
          card.innerHTML = `
            <div class="app-left">
              <div class="metric-icon">
                <svg class="icon-svg" viewBox="0 0 24 24"><path d="M6 2L3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z"/><line x1="3" y1="6" x2="21" y2="6"/></svg>
              </div>
              <div class="app-meta">
                <div class="app-name-row">
                  <span class="app-name">${'$'}{app.name}</span>
                  <span class="tag-badge">${'$'}{app.latestVersion}</span>
                </div>
                <div class="app-pkg">${'$'}{app.packageName} • ${'$'}{app.author}</div>
              </div>
            </div>
            <div class="app-actions">
              <button class="btn btn-sm" onclick="installStoreApp('${'$'}{app.id}')">
                <svg class="icon-svg" viewBox="0 0 24 24"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
                <span>Install on Device</span>
              </button>
            </div>
          `;
          box.appendChild(card);
        });
      });
    }

    function submitBulkRepos() {
      const raw = document.getElementById('bulkRepoTextarea').value.trim();
      if (!raw) return;
      closeModal('repoModal');
      showToast('Syncing repositories...', 'info');
      fetch('/api/store/add-repos', {method:'POST', body:JSON.stringify({rawInput:raw})})
        .then(r => r.json()).then(d => {
          showToast(d.message, 'success');
          setTimeout(loadStore, 1500);
        });
    }

    function installStoreApp(id) {
      fetch('/api/store/install', {method:'POST', body:JSON.stringify({id})})
        .then(r => r.json()).then(d => showToast(d.message, 'success'));
    }

    function cleanRemoteCache() {
      fetch('/api/system/clean-cache', {method:'POST'})
        .then(r => r.json()).then(d => {
          showToast(d.message, d.status === 'ok' ? 'success' : 'error');
          pollTelemetry();
        });
    }

    // --- Live Telemetry & Session Polling ---
    function pollTelemetry() {
      fetch('/status').then(r => r.json()).then(d => {
        if (d.storageText) document.getElementById('telemetryStorage').textContent = d.storageText;
        if (d.storageUsedPct) document.getElementById('storageMeter').style.width = d.storageUsedPct + '%';
        if (d.ramText) document.getElementById('telemetryRam').textContent = d.ramText;
        if (d.ramUsedPct) document.getElementById('ramMeter').style.width = d.ramUsedPct + '%';
        if (d.privilegeText) document.getElementById('telemetryPrivilege').textContent = d.privilegeText;
        if (d.sessionStatus) {
          document.getElementById('telemetrySession').textContent = d.sessionStatus + (d.parsedAppLabel ? (' • ' + d.parsedAppLabel) : '');
          document.getElementById('deviceSessionTask').textContent = d.sessionTask || 'Ready';
          document.getElementById('deviceSessionBar').style.width = Math.round((d.sessionProgress || 0) * 100) + '%';
          currentParsedPkg = d.parsedPackageName || '';
        }
        if (d.logs) {
          const logBox = document.getElementById('serverLogBox');
          logBox.innerHTML = d.logs.map(l => '<div class="log-line">&gt; ' + l + '</div>').join('');
          logBox.scrollTop = logBox.scrollHeight;
        }
      }).catch(() => {});
    }

    // Initialize Theme (Light default), Language (Auto-detect), and Telemetry Polling
    const savedTheme = localStorage.getItem('omni_remote_theme') || 'light';
    setThemeMode(savedTheme);
    const savedLang = localStorage.getItem('omni_remote_lang') || 'auto';
    document.getElementById('langSelect').value = savedLang;
    changeLanguage(savedLang);
    pollTelemetry();
    setInterval(pollTelemetry, 2500);
  </script>
</body>
</html>
    """.trimIndent()
  }
}
