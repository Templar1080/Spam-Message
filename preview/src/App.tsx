import React, { useMemo, useState } from 'react';
import {
  AlertTriangle,
  Ban,
  Check,
  CheckCircle2,
  Copy,
  Download,
  FlaskConical,
  Inbox,
  Lock,
  Plus,
  RotateCcw,
  Search,
  Send,
  Settings2,
  Shield,
  ShieldAlert,
  ShieldCheck,
  Smartphone,
  Sparkles,
  Trash2,
  WifiOff,
} from 'lucide-react';
import {
  ClassificationResult,
  CustomRule,
  FilterMode,
  SmsCategory,
  classifyIranSms,
} from './engine/iranSmsClassifier';
import {
  INITIAL_IRANIAN_MESSAGES,
  SIMULATION_SCENARIOS,
} from './data/sampleIranianMessages';

interface StoredSms {
  id: string;
  sender: string;
  displayName?: string;
  body: string;
  minutesAgo: number;
  isSavedContact?: boolean;
  manualOverride?: 'RESTORED' | 'BLOCKED';
}

interface ToastBanner {
  id: number;
  type: 'OTP' | 'ALLOWED' | 'BLOCKED' | 'COPIED';
  title: string;
  subtitle: string;
  otpCode?: string | null;
}

type PhoneTab = 'inbox' | 'otp' | 'blocked' | 'rules' | 'shield';
type WorkbenchTab = 'simulator' | 'install';

export default function App() {
  const [messages, setMessages] = useState<StoredSms[]>(INITIAL_IRANIAN_MESSAGES);
  const [customRules, setCustomRules] = useState<CustomRule[]>([
    { id: 'r-1', pattern: '500041122', target: 'SENDER', action: 'BLOCK' },
  ]);
  const [shieldEnabled, setShieldEnabled] = useState(true);
  const [filterMode, setFilterMode] = useState<FilterMode>('ALLOW_OTP_AND_BANK');
  const [neverBlockOtp, setNeverBlockOtp] = useState(true);
  const [autoCopyOtp, setAutoCopyOtp] = useState(false);

  const [phoneTab, setPhoneTab] = useState<PhoneTab>('inbox');
  const [searchQuery, setSearchQuery] = useState('');
  const [copiedCode, setCopiedCode] = useState<string | null>(null);
  const [banner, setBanner] = useState<ToastBanner | null>(null);

  // Rule creator form inside phone
  const [newRulePattern, setNewRulePattern] = useState('');
  const [newRuleTarget, setNewRuleTarget] = useState<'SENDER' | 'BODY'>('SENDER');
  const [newRuleAction, setNewRuleAction] = useState<'BLOCK' | 'ALLOW'>('BLOCK');

  // Right-side workbench state
  const [workbenchTab, setWorkbenchTab] = useState<WorkbenchTab>('simulator');
  const [testSender, setTestSender] = useState('9850004912');
  const [testBody, setTestBody] = useState(
    'فروشگاه اینترنتی دیجی‌کالا\nکد تایید شما: ۶۸۳۹۲۰\nلغو11'
  );

  // Classify all stored messages dynamically based on current shield settings & rules
  const classifiedMessages = useMemo(() => {
    return messages.map((msg) => {
      const baseResult = classifyIranSms({
        sender: msg.sender,
        body: msg.body,
        isSavedContact: msg.isSavedContact,
        filterMode,
        customRules,
        neverBlockOtpEvenFromBlocked: neverBlockOtp,
      });

      let isBlocked = shieldEnabled && baseResult.shouldBlock;
      let category = baseResult.category;
      let reasonSummary = baseResult.reasonSummary;

      if (msg.manualOverride === 'RESTORED') {
        isBlocked = false;
        category = baseResult.otpCode ? 'OTP' : 'PERSONAL';
        reasonSummary = 'Restored by you & sender whitelisted.';
      } else if (msg.manualOverride === 'BLOCKED') {
        isBlocked = true;
        category = 'SPAM_AD';
        reasonSummary = 'Manually marked as spam.';
      }

      return {
        ...msg,
        result: {
          ...baseResult,
          category,
          shouldBlock: isBlocked,
          reasonSummary,
        },
        isBlocked,
      };
    });
  }, [messages, filterMode, customRules, neverBlockOtp, shieldEnabled]);

  const allowedList = useMemo(
    () => classifiedMessages.filter((m) => !m.isBlocked),
    [classifiedMessages]
  );
  const otpAndBankList = useMemo(
    () =>
      classifiedMessages.filter(
        (m) =>
          !m.isBlocked &&
          (m.result.category === 'OTP' || m.result.category === 'BANK_ALERT')
      ),
    [classifiedMessages]
  );
  const blockedList = useMemo(
    () => classifiedMessages.filter((m) => m.isBlocked),
    [classifiedMessages]
  );

  // Live classification for custom tester on the right panel
  const liveAnalysis: ClassificationResult = useMemo(() => {
    return classifyIranSms({
      sender: testSender,
      body: testBody,
      isSavedContact: false,
      filterMode,
      customRules,
      neverBlockOtpEvenFromBlocked: neverBlockOtp,
    });
  }, [testSender, testBody, filterMode, customRules, neverBlockOtp]);

  const showTimedBanner = (next: ToastBanner) => {
    setBanner(next);
    window.setTimeout(() => {
      setBanner((prev) => (prev?.id === next.id ? null : prev));
    }, 4500);
  };

  const handleCopyOtp = (code: string) => {
    navigator.clipboard?.writeText(code).catch(() => {});
    setCopiedCode(code);
    showTimedBanner({
      id: Date.now(),
      type: 'COPIED',
      title: `Copied OTP: ${code}`,
      subtitle: 'Ready to paste into your app or Shaparak payment page',
    });
    window.setTimeout(() => {
      setCopiedCode((prev) => (prev === code ? null : prev));
    }, 2200);
  };

  const fireIncomingSms = (sender: string, body: string) => {
    const evalRes = classifyIranSms({
      sender,
      body,
      filterMode,
      customRules,
      neverBlockOtpEvenFromBlocked: neverBlockOtp,
    });

    const willBlock = shieldEnabled && evalRes.shouldBlock;
    const newMsg: StoredSms = {
      id: `sim-${Date.now()}`,
      sender,
      body,
      minutesAgo: 0,
    };

    setMessages((prev) => [newMsg, ...prev]);

    if (willBlock) {
      showTimedBanner({
        id: Date.now(),
        type: 'BLOCKED',
        title:
          evalRes.category === 'PHISHING'
            ? `Blocked Phishing Scam (${sender})`
            : `Silently Blocked Ad (${sender})`,
        subtitle: 'Zero phone chime • Quarantined in Blocked tab',
      });
    } else if (evalRes.category === 'OTP' && evalRes.otpCode) {
      if (autoCopyOtp) {
        navigator.clipboard?.writeText(evalRes.otpCode).catch(() => {});
      }
      showTimedBanner({
        id: Date.now(),
        type: 'OTP',
        title: `OTP ${evalRes.otpCode} • ${sender}`,
        subtitle: autoCopyOtp
          ? 'Auto-copied to clipboard! Verification allowed through.'
          : 'Allowed immediately • Tap to copy verification code',
        otpCode: evalRes.otpCode,
      });
    } else {
      showTimedBanner({
        id: Date.now(),
        type: 'ALLOWED',
        title: `New SMS • ${sender}`,
        subtitle: body.slice(0, 64),
      });
    }
  };

  const handleRestoreMessage = (msg: StoredSms) => {
    setMessages((prev) =>
      prev.map((m) => (m.id === msg.id ? { ...m, manualOverride: 'RESTORED' } : m))
    );
    setCustomRules((prev) => [
      {
        id: `rule-${Date.now()}`,
        pattern: msg.sender,
        target: 'SENDER',
        action: 'ALLOW',
      },
      ...prev,
    ]);
  };

  const handleBlockSender = (msg: StoredSms) => {
    setMessages((prev) =>
      prev.map((m) => (m.id === msg.id ? { ...m, manualOverride: 'BLOCKED' } : m))
    );
    setCustomRules((prev) => [
      {
        id: `rule-${Date.now()}`,
        pattern: msg.sender,
        target: 'SENDER',
        action: 'BLOCK',
      },
      ...prev,
    ]);
  };

  const handleDeleteMessage = (id: string) => {
    setMessages((prev) => prev.filter((m) => m.id !== id));
  };

  const handleAddRule = () => {
    const trimmed = newRulePattern.trim();
    if (!trimmed) return;
    setCustomRules((prev) => [
      {
        id: `rule-${Date.now()}`,
        pattern: trimmed,
        target: newRuleTarget,
        action: newRuleAction,
      },
      ...prev,
    ]);
    setNewRulePattern('');
  };

  const activePhoneMessages = useMemo(() => {
    const source =
      phoneTab === 'otp'
        ? otpAndBankList
        : phoneTab === 'blocked'
          ? blockedList
          : allowedList;
    const q = searchQuery.trim().toLowerCase();
    if (!q) return source;
    return source.filter(
      (m) =>
        m.sender.toLowerCase().includes(q) ||
        (m.displayName?.toLowerCase().includes(q) ?? false) ||
        m.body.toLowerCase().includes(q) ||
        (m.result.otpCode?.includes(q) ?? false)
    );
  }, [phoneTab, otpAndBankList, blockedList, allowedList, searchQuery]);

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-950 via-slate-900 to-emerald-950/40 text-slate-100">
      {/* Top Header */}
      <header className="border-b border-slate-800/80 bg-slate-950/70 backdrop-blur-md">
        <div className="mx-auto flex max-w-7xl flex-wrap items-center justify-between gap-4 px-4 py-3.5 sm:px-6">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-emerald-500/15 ring-1 ring-emerald-500/40">
              <ShieldCheck className="h-6 w-6 text-emerald-400" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h1 className="text-lg font-bold tracking-tight text-white">
                  ParsShield
                </h1>
                <span className="rounded-full bg-emerald-500/15 px-2.5 py-0.5 text-xs font-semibold text-emerald-300 ring-1 ring-emerald-500/30">
                  Android Native (Kotlin + Compose)
                </span>
              </div>
              <p className="text-xs text-slate-400">
                Iran SMS Spam, Ad & Phishing Blocker • Zero False-Positive OTP Protection
              </p>
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-2.5 text-xs">
            <span className="inline-flex items-center gap-1.5 rounded-lg bg-slate-900 px-3 py-1.5 text-slate-300 ring-1 ring-slate-800">
              <WifiOff className="h-3.5 w-3.5 text-emerald-400" />
              100% Offline • No Internet Permission
            </span>
            <button
              onClick={() => setWorkbenchTab('install')}
              className="inline-flex items-center gap-1.5 rounded-lg bg-emerald-500 px-3.5 py-1.5 font-semibold text-slate-950 transition hover:bg-emerald-400"
            >
              <Download className="h-3.5 w-3.5" />
              Get Android APK
            </button>
          </div>
        </div>
      </header>

      {/* Main Split Layout: Interactive Android Device + Live Iranian SMS Workbench */}
      <main className="mx-auto grid max-w-7xl grid-cols-1 gap-8 px-4 py-6 sm:px-6 lg:grid-cols-12 lg:items-start">
        {/* LEFT COLUMN: Realistic Android Phone Preview */}
        <div className="flex flex-col items-center lg:col-span-5">
          <div className="relative w-full max-w-[390px] overflow-hidden rounded-[2.6rem] border-[7px] border-slate-800 bg-slate-950 shadow-2xl shadow-black/70 ring-1 ring-slate-700/60">
            {/* Android Status Bar + Camera Punch-hole */}
            <div className="flex items-center justify-between bg-slate-950 px-6 pb-2 pt-3 text-[11px] font-medium text-slate-400">
              <span>19:42 • IR-MCI</span>
              <div className="h-3.5 w-3.5 rounded-full bg-slate-900 ring-1 ring-slate-800" />
              <div className="flex items-center gap-1.5">
                <span className="text-emerald-400">5G</span>
                <span>94%</span>
              </div>
            </div>

            {/* Simulated Android Notification Shade Toast */}
            {banner && (
              <div className="mx-3 mb-2 rounded-2xl border border-slate-700/80 bg-slate-900/95 p-3 shadow-xl backdrop-blur">
                <div className="flex items-start justify-between gap-2">
                  <div className="flex items-start gap-2.5">
                    {banner.type === 'BLOCKED' ? (
                      <Ban className="mt-0.5 h-4 w-4 shrink-0 text-amber-400" />
                    ) : (
                      <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-emerald-400" />
                    )}
                    <div>
                      <p className="text-xs font-bold text-white">{banner.title}</p>
                      <p className="mt-0.5 text-[11px] text-slate-300">{banner.subtitle}</p>
                    </div>
                  </div>
                  {banner.otpCode && (
                    <button
                      onClick={() => handleCopyOtp(banner.otpCode!)}
                      className="shrink-0 rounded-lg bg-emerald-500 px-2.5 py-1 font-mono text-[11px] font-bold text-slate-950 hover:bg-emerald-400"
                    >
                      Copy {banner.otpCode}
                    </button>
                  )}
                </div>
              </div>
            )}

            {/* App Top Bar */}
            <div className="flex items-center justify-between border-b border-slate-800/80 bg-slate-900/60 px-4 py-3">
              <div className="flex items-center gap-2.5">
                <Shield className="h-5 w-5 text-emerald-400" />
                <div>
                  <div className="text-sm font-extrabold text-white">ParsShield</div>
                  <div className="text-[11px] text-slate-400">
                    {blockedList.length} ads blocked • {otpAndBankList.length} OTP/Bank safe
                  </div>
                </div>
              </div>
              <span
                className={`rounded-full px-2.5 py-0.5 text-[10px] font-bold ${
                  shieldEnabled
                    ? 'bg-emerald-500/15 text-emerald-300 ring-1 ring-emerald-500/30'
                    : 'bg-rose-500/15 text-rose-300 ring-1 ring-rose-500/30'
                }`}
              >
                {shieldEnabled ? 'SHIELD ON' : 'PAUSED'}
              </span>
            </div>

            {/* Phone Screen Body */}
            <div className="phone-scroll h-[540px] overflow-y-auto bg-slate-950 px-3.5 py-3">
              {(phoneTab === 'inbox' || phoneTab === 'otp' || phoneTab === 'blocked') && (
                <div className="space-y-3">
                  <div className="flex items-center justify-between">
                    <div>
                      <h2 className="text-base font-bold text-white">
                        {phoneTab === 'inbox' && 'Clean Inbox'}
                        {phoneTab === 'otp' && 'OTPs & Bank Alerts'}
                        {phoneTab === 'blocked' && 'Blocked Spam & Phishing'}
                      </h2>
                      <p className="text-[11px] text-slate-400">
                        {phoneTab === 'inbox' &&
                          'Personal, OTP, Shaparak & verified service SMS'}
                        {phoneTab === 'otp' &&
                          'Login codes, رمز پویا & bank receipts in one place'}
                        {phoneTab === 'blocked' &&
                          'Silently quarantined ads, clinics, villas & ثنا scams'}
                      </p>
                    </div>
                    {phoneTab === 'blocked' && blockedList.length > 0 && (
                      <button
                        onClick={() =>
                          setMessages((prev) =>
                            prev.filter(
                              (m) =>
                                !blockedList.some((blocked) => blocked.id === m.id)
                            )
                          )
                        }
                        className="text-xs font-semibold text-rose-400 hover:text-rose-300"
                      >
                        Clear All
                      </button>
                    )}
                  </div>

                  {/* Search Input */}
                  <div className="relative">
                    <Search className="pointer-events-none absolute left-3 top-2.5 h-4 w-4 text-slate-500" />
                    <input
                      type="text"
                      value={searchQuery}
                      onChange={(e) => setSearchQuery(e.target.value)}
                      placeholder="Search sender, Persian/English text, OTP..."
                      className="w-full rounded-xl border border-slate-800 bg-slate-900/90 py-2 pl-9 pr-3 text-xs text-slate-100 placeholder-slate-500 focus:border-emerald-500 focus:outline-none"
                    />
                  </div>

                  {activePhoneMessages.length === 0 ? (
                    <div className="flex flex-col items-center justify-center py-16 text-center">
                      <Inbox className="h-9 w-9 text-slate-600" />
                      <p className="mt-2 text-xs font-semibold text-slate-300">
                        No messages in this view
                      </p>
                      <p className="mt-1 max-w-[230px] text-[11px] text-slate-500">
                        Use the simulator on the right to send real Iranian SMS examples!
                      </p>
                    </div>
                  ) : (
                    <div className="space-y-2.5">
                      {activePhoneMessages.map((msg) => (
                        <div
                          key={msg.id}
                          className="rounded-2xl border border-slate-800/90 bg-slate-900/75 p-3 transition hover:border-slate-700"
                        >
                          <div className="flex items-start justify-between gap-2">
                            <div>
                              <div className="text-xs font-bold text-white">
                                {msg.displayName ?? msg.sender}
                              </div>
                              {msg.displayName && (
                                <div className="text-[10px] text-slate-500">{msg.sender}</div>
                              )}
                            </div>
                            <CategoryBadge category={msg.result.category} />
                          </div>

                          <p
                            dir="auto"
                            className="mt-2 whitespace-pre-line text-xs leading-relaxed text-slate-200"
                          >
                            {msg.body}
                          </p>

                          {msg.result.otpCode && (
                            <button
                              onClick={() => handleCopyOtp(msg.result.otpCode!)}
                              className="mt-2.5 inline-flex items-center gap-1.5 rounded-lg bg-emerald-500/15 px-2.5 py-1 font-mono text-xs font-bold text-emerald-300 ring-1 ring-emerald-500/30 transition hover:bg-emerald-500/25"
                            >
                              {copiedCode === msg.result.otpCode ? (
                                <>
                                  <Check className="h-3.5 w-3.5" />
                                  Copied {msg.result.otpCode}
                                </>
                              ) : (
                                <>
                                  <Copy className="h-3.5 w-3.5" />
                                  Copy OTP: {msg.result.otpCode}
                                </>
                              )}
                            </button>
                          )}

                          {msg.result.category === 'PHISHING' && (
                            <div className="mt-2 flex items-start gap-1.5 rounded-lg bg-rose-500/15 p-2 text-[11px] text-rose-300 ring-1 ring-rose-500/30">
                              <AlertTriangle className="mt-0.5 h-3.5 w-3.5 shrink-0 text-rose-400" />
                              <span>{msg.result.reasonSummary}</span>
                            </div>
                          )}

                          <div className="mt-2.5 flex items-center justify-between border-t border-slate-800/70 pt-2 text-[11px] text-slate-500">
                            <span>
                              {msg.minutesAgo === 0 ? 'Just now' : `${msg.minutesAgo}m ago`}
                            </span>
                            <div className="flex items-center gap-2">
                              {msg.isBlocked ? (
                                <button
                                  onClick={() => handleRestoreMessage(msg)}
                                  className="inline-flex items-center gap-1 font-semibold text-emerald-400 hover:text-emerald-300"
                                >
                                  <RotateCcw className="h-3 w-3" />
                                  Not Spam
                                </button>
                              ) : (
                                msg.result.category !== 'OTP' && (
                                  <button
                                    onClick={() => handleBlockSender(msg)}
                                    className="inline-flex items-center gap-1 font-medium text-amber-400/90 hover:text-amber-300"
                                  >
                                    <Ban className="h-3 w-3" />
                                    Block Ad
                                  </button>
                                )
                              )}
                              <button
                                onClick={() => handleDeleteMessage(msg.id)}
                                className="text-slate-500 hover:text-rose-400"
                                title="Delete message"
                              >
                                <Trash2 className="h-3.5 w-3.5" />
                              </button>
                            </div>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )}

              {/* Custom Rules Tab */}
              {phoneTab === 'rules' && (
                <div className="space-y-3">
                  <div>
                    <h2 className="text-base font-bold text-white">Custom Filter Rules</h2>
                    <p className="text-[11px] text-slate-400">
                      Whitelist or block senders & Persian/English keywords. OTPs stay safe!
                    </p>
                  </div>

                  <div className="space-y-2.5 rounded-2xl border border-slate-800 bg-slate-900/80 p-3">
                    <div className="flex gap-1.5">
                      <button
                        onClick={() => setNewRuleAction('BLOCK')}
                        className={`flex-1 rounded-lg py-1.5 text-xs font-semibold ${
                          newRuleAction === 'BLOCK'
                            ? 'bg-amber-500/20 text-amber-300 ring-1 ring-amber-500/40'
                            : 'bg-slate-800 text-slate-400'
                        }`}
                      >
                        Block
                      </button>
                      <button
                        onClick={() => setNewRuleAction('ALLOW')}
                        className={`flex-1 rounded-lg py-1.5 text-xs font-semibold ${
                          newRuleAction === 'ALLOW'
                            ? 'bg-emerald-500/20 text-emerald-300 ring-1 ring-emerald-500/40'
                            : 'bg-slate-800 text-slate-400'
                        }`}
                      >
                        Always Allow
                      </button>
                    </div>

                    <div className="flex gap-1.5">
                      <button
                        onClick={() => setNewRuleTarget('SENDER')}
                        className={`flex-1 rounded-lg py-1 text-[11px] font-medium ${
                          newRuleTarget === 'SENDER'
                            ? 'bg-slate-700 text-white'
                            : 'bg-slate-900 text-slate-400'
                        }`}
                      >
                        Sender Number
                      </button>
                      <button
                        onClick={() => setNewRuleTarget('BODY')}
                        className={`flex-1 rounded-lg py-1 text-[11px] font-medium ${
                          newRuleTarget === 'BODY'
                            ? 'bg-slate-700 text-white'
                            : 'bg-slate-900 text-slate-400'
                        }`}
                      >
                        Message Keyword
                      </button>
                    </div>

                    <div className="flex gap-2">
                      <input
                        type="text"
                        dir="auto"
                        value={newRulePattern}
                        onChange={(e) => setNewRulePattern(e.target.value)}
                        placeholder={
                          newRuleTarget === 'SENDER'
                            ? 'e.g. 50004912 or +9821...'
                            : 'e.g. کاشت مو or تور کیش'
                        }
                        className="flex-1 rounded-xl border border-slate-700 bg-slate-950 px-3 py-1.5 text-xs text-white placeholder-slate-500 focus:border-emerald-500 focus:outline-none"
                      />
                      <button
                        onClick={handleAddRule}
                        className="inline-flex items-center gap-1 rounded-xl bg-emerald-500 px-3 py-1.5 text-xs font-bold text-slate-950 hover:bg-emerald-400"
                      >
                        <Plus className="h-3.5 w-3.5" />
                        Add
                      </button>
                    </div>
                  </div>

                  <div className="space-y-2">
                    {customRules.map((rule) => (
                      <div
                        key={rule.id}
                        className="flex items-center justify-between rounded-xl border border-slate-800 bg-slate-900/60 px-3 py-2"
                      >
                        <div>
                          <div dir="auto" className="text-xs font-bold text-white">
                            {rule.pattern}
                          </div>
                          <div
                            className={`text-[10px] font-semibold ${
                              rule.action === 'ALLOW' ? 'text-emerald-400' : 'text-amber-400'
                            }`}
                          >
                            {rule.action} • {rule.target}
                          </div>
                        </div>
                        <button
                          onClick={() =>
                            setCustomRules((prev) => prev.filter((r) => r.id !== rule.id))
                          }
                          className="text-slate-500 hover:text-rose-400"
                        >
                          <Trash2 className="h-4 w-4" />
                        </button>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* Shield Settings Tab */}
              {phoneTab === 'shield' && (
                <div className="space-y-3">
                  <div className="rounded-2xl border border-emerald-500/30 bg-emerald-500/10 p-3.5">
                    <div className="flex items-center justify-between">
                      <div>
                        <div className="text-xs font-bold text-white">
                          Master Spam & Ad Shield
                        </div>
                        <div className="text-[11px] text-emerald-200/80">
                          100% offline • Zero network access
                        </div>
                      </div>
                      <input
                        type="checkbox"
                        checked={shieldEnabled}
                        onChange={(e) => setShieldEnabled(e.target.checked)}
                        className="h-4 w-4 accent-emerald-500"
                      />
                    </div>
                  </div>

                  <div className="space-y-2 rounded-2xl border border-slate-800 bg-slate-900/80 p-3.5">
                    <div className="text-xs font-bold text-white">Filtering Mode</div>
                    {(
                      [
                        {
                          id: 'ALLOW_OTP_AND_BANK',
                          title: 'Allow OTPs + Bank & Service Alerts',
                          desc: 'Blocks ads/promos & phishing; keeps OTPs, Shaparak & delivery alerts.',
                        },
                        {
                          id: 'STRICT_OTP_ONLY',
                          title: 'Strict: OTPs & Personal Numbers Only',
                          desc: 'Blocks all bulk/shortcode SMS except login OTPs & رمز پویا.',
                        },
                        {
                          id: 'MONITOR_ONLY',
                          title: 'Monitor & Tag Only',
                          desc: 'Labels spam without suppressing notifications.',
                        },
                      ] as const
                    ).map((opt) => (
                      <label
                        key={opt.id}
                        className={`flex cursor-pointer items-start gap-2.5 rounded-xl border p-2.5 transition ${
                          filterMode === opt.id
                            ? 'border-emerald-500/50 bg-emerald-500/10'
                            : 'border-slate-800 bg-slate-950/60'
                        }`}
                      >
                        <input
                          type="radio"
                          name="filterMode"
                          checked={filterMode === opt.id}
                          onChange={() => setFilterMode(opt.id)}
                          className="mt-0.5 accent-emerald-500"
                        />
                        <div>
                          <div className="text-xs font-semibold text-white">{opt.title}</div>
                          <div className="text-[11px] text-slate-400">{opt.desc}</div>
                        </div>
                      </label>
                    ))}
                  </div>

                  <div className="space-y-2.5 rounded-2xl border border-slate-800 bg-slate-900/80 p-3.5">
                    <div className="text-xs font-bold text-white">OTP Safety Guardrails</div>
                    <label className="flex items-center justify-between gap-3">
                      <div>
                        <div className="text-xs font-medium text-slate-200">
                          Never block OTPs from blocked senders
                        </div>
                        <div className="text-[10px] text-slate-400">
                          Protects OTPs on shared 1000–5000 gateways
                        </div>
                      </div>
                      <input
                        type="checkbox"
                        checked={neverBlockOtp}
                        onChange={(e) => setNeverBlockOtp(e.target.checked)}
                        className="h-4 w-4 accent-emerald-500"
                      />
                    </label>
                    <label className="flex items-center justify-between gap-3">
                      <div>
                        <div className="text-xs font-medium text-slate-200">
                          Auto-copy OTP to clipboard
                        </div>
                        <div className="text-[10px] text-slate-400">
                          Instant paste for login codes & رمز پویا
                        </div>
                      </div>
                      <input
                        type="checkbox"
                        checked={autoCopyOtp}
                        onChange={(e) => setAutoCopyOtp(e.target.checked)}
                        className="h-4 w-4 accent-emerald-500"
                      />
                    </label>
                  </div>
                </div>
              )}
            </div>

            {/* Android Bottom Navigation Bar */}
            <nav className="grid grid-cols-5 border-t border-slate-800 bg-slate-900/95 px-1 py-2 text-[10px]">
              <PhoneNavBtn
                active={phoneTab === 'inbox'}
                onClick={() => setPhoneTab('inbox')}
                icon={<Inbox className="h-4 w-4" />}
                label="Inbox"
              />
              <PhoneNavBtn
                active={phoneTab === 'otp'}
                onClick={() => setPhoneTab('otp')}
                icon={<Lock className="h-4 w-4" />}
                label="OTP/Bank"
              />
              <PhoneNavBtn
                active={phoneTab === 'blocked'}
                onClick={() => setPhoneTab('blocked')}
                icon={<Ban className="h-4 w-4" />}
                label="Blocked"
                badge={blockedList.length}
              />
              <PhoneNavBtn
                active={phoneTab === 'rules'}
                onClick={() => setPhoneTab('rules')}
                icon={<Settings2 className="h-4 w-4" />}
                label="Rules"
              />
              <PhoneNavBtn
                active={phoneTab === 'shield'}
                onClick={() => setPhoneTab('shield')}
                icon={<Shield className="h-4 w-4" />}
                label="Shield"
              />
            </nav>

            {/* Android Gesture Pill */}
            <div className="flex justify-center bg-slate-950 py-2">
              <div className="h-1 w-28 rounded-full bg-slate-700" />
            </div>
          </div>
        </div>

        {/* RIGHT COLUMN: Live Iranian SMS Classifier Workbench & APK Install Guide */}
        <div className="space-y-6 lg:col-span-7">
          {/* Mode Switcher */}
          <div className="flex gap-2 rounded-2xl border border-slate-800 bg-slate-900/70 p-1.5">
            <button
              onClick={() => setWorkbenchTab('simulator')}
              className={`flex flex-1 items-center justify-center gap-2 rounded-xl py-2.5 text-xs font-bold transition ${
                workbenchTab === 'simulator'
                  ? 'bg-emerald-500 text-slate-950 shadow'
                  : 'text-slate-300 hover:text-white'
              }`}
            >
              <FlaskConical className="h-4 w-4" />
              Live Iranian SMS Tester & Simulator
            </button>
            <button
              onClick={() => setWorkbenchTab('install')}
              className={`flex flex-1 items-center justify-center gap-2 rounded-xl py-2.5 text-xs font-bold transition ${
                workbenchTab === 'install'
                  ? 'bg-emerald-500 text-slate-950 shadow'
                  : 'text-slate-300 hover:text-white'
              }`}
            >
              <Smartphone className="h-4 w-4" />
              How to Install on Your Android Phone
            </button>
          </div>

          {workbenchTab === 'simulator' ? (
            <div className="space-y-5">
              {/* Quick 1-Tap Incoming SMS Scenarios */}
              <section className="rounded-2xl border border-slate-800 bg-slate-900/60 p-5">
                <div className="flex items-center justify-between gap-2">
                  <div>
                    <h2 className="flex items-center gap-2 text-base font-bold text-white">
                      <Sparkles className="h-4 w-4 text-emerald-400" />
                      Simulate Incoming Iranian SMS to Phone
                    </h2>
                    <p className="mt-0.5 text-xs text-slate-400">
                      Click any real-world Iranian SMS below to watch how Android handles OTPs
                      (even with لغو11!), Shaparak رمز پویا, bank receipts, ads, and ثنا phishing.
                    </p>
                  </div>
                </div>

                <div className="mt-4 flex flex-wrap gap-2">
                  {SIMULATION_SCENARIOS.map((sc) => (
                    <button
                      key={sc.label}
                      onClick={() => {
                        setTestSender(sc.sender);
                        setTestBody(sc.body);
                        fireIncomingSms(sc.sender, sc.body);
                      }}
                      className="inline-flex items-center gap-2 rounded-xl border border-slate-700/80 bg-slate-950/80 px-3 py-2 text-xs font-semibold text-slate-200 transition hover:border-emerald-500/60 hover:bg-slate-800"
                    >
                      <span
                        className={`h-2 w-2 rounded-full ${
                          sc.badge === 'OTP'
                            ? 'bg-emerald-400'
                            : sc.badge === 'BANK'
                              ? 'bg-sky-400'
                              : sc.badge === 'PHISHING'
                                ? 'bg-rose-500'
                                : 'bg-amber-400'
                        }`}
                      />
                      {sc.label}
                    </button>
                  ))}
                </div>
              </section>

              {/* Custom SMS Inspector */}
              <section className="rounded-2xl border border-slate-800 bg-slate-900/60 p-5">
                <h2 className="text-base font-bold text-white">
                  Test Any SMS From Your Own Phone
                </h2>
                <p className="mt-0.5 text-xs text-slate-400">
                  Paste any annoying ad or important OTP (Persian, Finglish, or English) to
                  inspect the engine’s real-time verdict and rule weights.
                </p>

                <div className="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-3">
                  <div className="sm:col-span-1">
                    <label className="mb-1 block text-xs font-medium text-slate-300">
                      Sender Number / ID
                    </label>
                    <input
                      type="text"
                      value={testSender}
                      onChange={(e) => setTestSender(e.target.value)}
                      placeholder="e.g. 9850004912 or +98912..."
                      className="w-full rounded-xl border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white focus:border-emerald-500 focus:outline-none"
                    />
                  </div>
                  <div className=" flex items-end sm:col-span-2">
                    <button
                      onClick={() => fireIncomingSms(testSender, testBody)}
                      disabled={!testBody.trim()}
                      className="inline-flex w-full items-center justify-center gap-2 rounded-xl bg-emerald-500 px-4 py-2 text-sm font-bold text-slate-950 transition hover:bg-emerald-400 disabled:opacity-40"
                    >
                      <Send className="h-4 w-4" />
                      Send This SMS Into Phone Preview
                    </button>
                  </div>
                </div>

                <div className="mt-3">
                  <label className="mb-1 block text-xs font-medium text-slate-300">
                    SMS Message Content (supports Persian ۰-۹, Kashida ـ, Finglish & English)
                  </label>
                  <textarea
                    rows={4}
                    dir="auto"
                    value={testBody}
                    onChange={(e) => setTestBody(e.target.value)}
                    className="w-full rounded-xl border border-slate-700 bg-slate-950 p-3 text-sm text-white focus:border-emerald-500 focus:outline-none"
                  />
                </div>

                {/* Real-time Verdict Card */}
                <div
                  className={`mt-4 rounded-2xl border p-4 ${
                    liveAnalysis.shouldBlock
                      ? 'border-rose-500/40 bg-rose-500/10'
                      : 'border-emerald-500/40 bg-emerald-500/10'
                  }`}
                >
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <div className="flex items-center gap-2">
                      {liveAnalysis.shouldBlock ? (
                        <ShieldAlert className="h-5 w-5 text-rose-400" />
                      ) : (
                        <CheckCircle2 className="h-5 w-5 text-emerald-400" />
                      )}
                      <span
                        className={`text-sm font-extrabold ${
                          liveAnalysis.shouldBlock ? 'text-rose-300' : 'text-emerald-300'
                        }`}
                      >
                        {liveAnalysis.shouldBlock ? 'BLOCKED' : 'ALLOWED'} •{' '}
                        {liveAnalysis.category}
                      </span>
                    </div>
                    <div className="flex items-center gap-3 text-xs text-slate-300">
                      <span>Sender: {liveAnalysis.senderType}</span>
                      <span className="font-mono font-bold">
                        {liveAnalysis.confidence}% confidence
                      </span>
                    </div>
                  </div>

                  <p className="mt-2 text-xs text-slate-200">{liveAnalysis.reasonSummary}</p>

                  {liveAnalysis.otpCode && (
                    <div className="mt-2.5 inline-flex items-center gap-2 rounded-lg bg-emerald-500/20 px-3 py-1 font-mono text-xs font-bold text-emerald-200">
                      Extracted OTP Code: {liveAnalysis.otpCode}
                    </div>
                  )}

                  <div className="mt-3 space-y-1.5">
                    <div className="text-[11px] font-semibold uppercase tracking-wider text-slate-400">
                      Matched Classification Signals
                    </div>
                    {liveAnalysis.signals.map((sig, idx) => (
                      <div
                        key={`${sig.code}-${idx}`}
                        className="flex items-center justify-between rounded-lg bg-slate-950/70 px-3 py-1.5 text-xs"
                      >
                        <span className="text-slate-200">{sig.label}</span>
                        <span
                          className={`font-mono font-bold ${
                            sig.weight > 0 ? 'text-amber-400' : 'text-emerald-400'
                          }`}
                        >
                          {sig.weight > 0 ? `+${sig.weight} spam` : `${sig.weight} trust`}
                        </span>
                      </div>
                    ))}
                  </div>
                </div>
              </section>

              {/* Why Generic Spam Blockers Fail in Iran */}
              <section className="grid grid-cols-1 gap-3 sm:grid-cols-3">
                <FeatureCard
                  title="Shared 1000–5000 Gateways"
                  desc="In Iran, Digikala, Snapp, Divar & banks send OTPs from the same 1000/2000/3000/5000 panels as spammers—even with auto-appended لغو11. ParsShield inspects content before blocking."
                />
                <FeatureCard
                  title="Promo Code ≠ OTP"
                  desc="Spammers send 'کد تخفیف: 5040' or 'کد قرعه‌کشی: 88219'. ParsShield separates promo/lottery codes from real login OTPs & Shaparak رمز پویا."
                />
                <FeatureCard
                  title="Fake ثنا & Subsidy Shield"
                  desc="Blocks 09xx SIM phishing links impersonating Judiciary Sana (ابلاغیه ثنا), Kalabarg, or card-freeze scams while allowing real ADLIRAN notices."
                />
              </section>
            </div>
          ) : (
            /* APK Installation & Android Modes Guide */
            <div className="space-y-4 rounded-2xl border border-slate-800 bg-slate-900/60 p-6">
              <h2 className="text-lg font-bold text-white">
                Install ParsShield on Your Android Phone
              </h2>
              <p className="text-sm text-slate-300">
                The full native Android app (<code className="text-emerald-300">app/</code>,
                Kotlin + Jetpack Compose, Android 8.0–15) is ready in this repository with an
                automated GitHub Actions APK builder.
              </p>

              <div className="space-y-3 pt-2">
                <StepBox
                  num="1"
                  title="Download the Signed APK (No PC or Android Studio Needed)"
                  body="Push/merge this branch on GitHub -> open the 'Actions' tab -> select 'Build ParsShield Android APK' -> download 'ParsShield-Android-APK' (contains ParsShield-v1.0.0-signed.apk, ready to install directly on your phone). Or build locally anytime via './gradlew assembleRelease'."
                />
                <StepBox
                  num="2"
                  title="Pick Your Preferred Android Blocking Mode Inside the App"
                  body="• Option A — Notification Shield (Keep Google/Samsung Messages!): Grants Notification Access so ParsShield instantly dismisses spam SMS notifications while leaving OTPs, bank alerts, and chats untouched. (On Android 13+, if 'Restricted setting' appears after APK install, open App Info -> ⋮ -> 'Allow restricted settings'.)
• Option B — Full Default SMS Blocker: Sets ParsShield as your Default SMS handler for 100% OS-level silent quarantine (zero ring, zero vibration on spam) + 1-tap 'Copy OTP' button in notifications."
                />
                <StepBox
                  num="3"
                  title="100% Offline & Zero Internet Permission"
                  body="Check Android App Info -> Permissions: ParsShield intentionally does NOT request android.permission.INTERNET. Your Shaparak رمز پویا, bank balances, and OTP codes can never leave your phone."
                />
              </div>
            </div>
          )}
        </div>
      </main>
    </div>
  );
}

function CategoryBadge({ category }: { category: SmsCategory }) {
  const styles: Record<SmsCategory, { label: string; cls: string }> = {
    OTP: {
      label: 'OTP • Protected',
      cls: 'bg-emerald-500/15 text-emerald-300 ring-emerald-500/30',
    },
    BANK_ALERT: {
      label: 'Bank / Shaparak',
      cls: 'bg-sky-500/15 text-sky-300 ring-sky-500/30',
    },
    SERVICE_ALERT: {
      label: 'Service Alert',
      cls: 'bg-violet-500/15 text-violet-300 ring-violet-500/30',
    },
    PERSONAL: {
      label: 'Personal',
      cls: 'bg-slate-700/50 text-slate-300 ring-slate-600/40',
    },
    SPAM_AD: {
      label: 'Blocked Ad',
      cls: 'bg-amber-500/15 text-amber-300 ring-amber-500/30',
    },
    PHISHING: {
      label: 'Phishing Scam',
      cls: 'bg-rose-500/20 text-rose-300 ring-rose-500/40',
    },
  };
  const item = styles[category];
  return (
    <span
      className={`shrink-0 rounded-md px-2 py-0.5 text-[10px] font-bold ring-1 ${item.cls}`}
    >
      {item.label}
    </span>
  );
}

function PhoneNavBtn({
  active,
  onClick,
  icon,
  label,
  badge,
}: {
  active: boolean;
  onClick: () => void;
  icon: React.ReactNode;
  label: string;
  badge?: number;
}) {
  return (
    <button
      onClick={onClick}
      className={`relative flex flex-col items-center gap-1 rounded-xl py-1 transition ${
        active ? 'text-emerald-400' : 'text-slate-400 hover:text-slate-200'
      }`}
    >
      <div className="relative">
        {icon}
        {badge !== undefined && badge > 0 && (
          <span className="absolute -right-2.5 -top-1.5 rounded-full bg-amber-500 px-1.5 text-[9px] font-extrabold text-slate-950">
            {badge}
          </span>
        )}
      </div>
      <span className="font-semibold">{label}</span>
    </button>
  );
}

function FeatureCard({ title, desc }: { title: string; desc: string }) {
  return (
    <div className="rounded-2xl border border-slate-800 bg-slate-900/50 p-4">
      <h3 className="text-xs font-bold text-emerald-300">{title}</h3>
      <p className="mt-1.5 text-xs leading-relaxed text-slate-400">{desc}</p>
    </div>
  );
}

function StepBox({
  num,
  title,
  body,
}: {
  num: string;
  title: string;
  body: string;
}) {
  return (
    <div className="rounded-xl border border-slate-800 bg-slate-950/70 p-4">
      <div className="flex items-center gap-2.5">
        <span className="flex h-6 w-6 items-center justify-center rounded-full bg-emerald-500 text-xs font-extrabold text-slate-950">
          {num}
        </span>
        <h3 className="text-sm font-bold text-white">{title}</h3>
      </div>
      <p className="mt-2 whitespace-pre-line text-xs leading-relaxed text-slate-300">
        {body}
      </p>
    </div>
  );
}
