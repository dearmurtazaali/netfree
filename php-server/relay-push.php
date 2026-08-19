<?php
// ============================================================================
// RELAY: PUSH — app calls this when it has internet and wants to leave
// messages behind for a recipient who isn't nearby right now.
//
// This is intentionally simple (flat JSON file as storage) so it's easy to
// swap for MySQL later. The app never needs to know that change happened —
// only this file and pull.php touch storage.
// ============================================================================

header('Content-Type: application/json');

$input = json_decode(file_get_contents('php://input'), true);
$messages = $input['messages'] ?? [];

if (empty($messages)) {
    http_response_code(400);
    echo json_encode(['error' => 'no messages provided']);
    exit;
}

$storeFile = __DIR__ . '/storage/queue.json';
if (!is_dir(__DIR__ . '/storage')) {
    mkdir(__DIR__ . '/storage', 0755, true);
}

$queue = file_exists($storeFile) ? json_decode(file_get_contents($storeFile), true) : [];

foreach ($messages as $msg) {
    // Expecting: { "to": "userId", "from": "userId", "cipherText": "...", "sentAt": 123456 }
    $queue[] = $msg;
}

file_put_contents($storeFile, json_encode($queue));

echo json_encode(['status' => 'stored', 'count' => count($messages)]);
