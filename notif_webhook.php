<?php
/**
 * ProSeller Notif Webhook Receiver
 * Upload file ini ke root website: prosellergamingstore.com/notif_webhook.php
 *
 * Kiriman dari Android app berformat JSON POST:
 * {
 *   "app": "com.gojek.app",
 *   "app_name": "GoPay",
 *   "title": "Pembayaran Berhasil",
 *   "text": "Kamu membayar Rp150.000 ke ...",
 *   "timestamp": 1715000000000,
 *   "datetime": "2026-05-07 18:30:00",
 *   "secret": "rahasia123"
 * }
 */

// ============================================================
// KONFIGURASI — SESUAIKAN INI
// ============================================================
define('SECRET_KEY', 'rahasia123');          // Harus sama dengan di app Android
define('LOG_FILE',   __DIR__ . '/notif_log.txt');  // Log file
define('FORWARD_TELEGRAM', false);           // Kirim ke Telegram? (opsional)
define('TELEGRAM_BOT_TOKEN', '');            // Token bot Telegram
define('TELEGRAM_CHAT_ID',   '');            // Chat ID Telegram
// ============================================================

header('Content-Type: application/json');
header('Access-Control-Allow-Origin: *');

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    http_response_code(405);
    echo json_encode(['ok' => false, 'error' => 'Method not allowed']);
    exit;
}

// Baca body
$body = file_get_contents('php://input');
$data = json_decode($body, true);

if (!$data) {
    http_response_code(400);
    echo json_encode(['ok' => false, 'error' => 'Invalid JSON']);
    exit;
}

// Validasi secret key
if (SECRET_KEY !== '' && ($data['secret'] ?? '') !== SECRET_KEY) {
    http_response_code(403);
    echo json_encode(['ok' => false, 'error' => 'Unauthorized']);
    exit;
}

// Ambil data notifikasi
$app      = htmlspecialchars($data['app']      ?? '', ENT_QUOTES);
$appName  = htmlspecialchars($data['app_name'] ?? '', ENT_QUOTES);
$title    = $data['title']    ?? '';
$text     = $data['text']     ?? '';
$datetime = $data['datetime'] ?? date('Y-m-d H:i:s');
$ts       = $data['timestamp'] ?? time() * 1000;

// ============================================================
// PROSES NOTIFIKASI
// ============================================================

// 1. Tulis ke log file
$logLine = "[$datetime] [$appName] TITLE: $title | TEXT: $text\n";
file_put_contents(LOG_FILE, $logLine, FILE_APPEND | LOCK_EX);

// 2. (Opsional) Coba cocokkan dengan order yang menunggu pembayaran
// Contoh: cari nominal di teks notif dan cocokkan dengan order pending
$nominal = extractNominal($text . ' ' . $title);

// 3. Forward ke Telegram (opsional)
if (FORWARD_TELEGRAM && TELEGRAM_BOT_TOKEN && TELEGRAM_CHAT_ID) {
    $msg = "💰 *Notif Masuk: $appName*\n"
         . "📌 $title\n"
         . "💬 $text\n"
         . "🕐 $datetime";
    if ($nominal > 0) {
        $msg .= "\n💵 Nominal terdeteksi: Rp " . number_format($nominal, 0, ',', '.');
    }
    sendTelegram($msg);
}

// 4. Coba auto-confirm order (jika ada integrasi order)
$orderConfirmed = false;
if ($nominal > 0) {
    $orderConfirmed = tryConfirmOrder($nominal, $appName, $text);
}

echo json_encode([
    'ok'       => true,
    'received' => [
        'app'      => $appName,
        'title'    => $title,
        'nominal'  => $nominal,
        'datetime' => $datetime,
    ],
    'order_confirmed' => $orderConfirmed,
]);


// ============================================================
// HELPER FUNCTIONS
// ============================================================

/**
 * Ekstrak nominal Rupiah dari teks notifikasi.
 * Menangani format: Rp150.000 / Rp 150,000 / IDR 150000 / 150.000
 */
function extractNominal(string $text): int {
    // Format: Rp 150.000 atau Rp150,000 atau IDR 150.000
    if (preg_match('/(?:Rp|IDR)[.\s]*([0-9]{1,3}(?:[.,][0-9]{3})*(?:[.,][0-9]{1,2})?)/i', $text, $m)) {
        $raw = preg_replace('/[^0-9]/', '', $m[1]);
        // Jika lebih dari 2 digit akhir bukan sen, ambil semua
        return (int) $raw;
    }
    return 0;
}

/**
 * Coba konfirmasi order yang menunggu pembayaran dengan nominal yang cocok.
 * Sesuaikan dengan struktur database kamu.
 */
function tryConfirmOrder(int $nominal, string $source, string $rawText): bool {
    // Contoh integrasi dengan database order proseller
    // Aktifkan blok ini jika kamu punya koneksi DB di file ini

    /*
    require_once __DIR__ . '/koneksi.php';
    try {
        $db = getDB();
        // Cari order pending dengan harga sama
        $stmt = $db->prepare("
            SELECT id FROM orders
            WHERE status = 'pending'
              AND price = ?
              AND created_at >= DATE_SUB(NOW(), INTERVAL 2 HOUR)
            ORDER BY created_at DESC
            LIMIT 1
        ");
        $stmt->execute([$nominal]);
        $order = $stmt->fetch();
        if ($order) {
            $db->prepare("UPDATE orders SET status='paid', paid_at=NOW(), paid_source=? WHERE id=?")
               ->execute([$source, $order['id']]);
            return true;
        }
    } catch (Throwable $e) {
        file_put_contents(LOG_FILE, "[ERROR] DB: " . $e->getMessage() . "\n", FILE_APPEND);
    }
    */

    return false;
}

/**
 * Kirim pesan ke Telegram.
 */
function sendTelegram(string $message): void {
    $url = 'https://api.telegram.org/bot' . TELEGRAM_BOT_TOKEN . '/sendMessage';
    $ch  = curl_init($url);
    curl_setopt_array($ch, [
        CURLOPT_POST           => true,
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_TIMEOUT        => 8,
        CURLOPT_POSTFIELDS     => http_build_query([
            'chat_id'    => TELEGRAM_CHAT_ID,
            'text'       => $message,
            'parse_mode' => 'Markdown',
        ]),
    ]);
    curl_exec($ch);
    curl_close($ch);
}
