<?php
// ============================================================================
// RELAY: PULL — app calls this whenever it briefly gets internet, to
// collect any messages other people left behind for this user while they
// were both offline.
// ============================================================================

header('Content-Type: application/json');

$userId = $_GET['user'] ?? null;
if (!$userId) {
    http_response_code(400);
    echo json_encode(['error' => 'user param required']);
    exit;
}

$storeFile = __DIR__ . '/storage/queue.json';
$queue = file_exists($storeFile) ? json_decode(file_get_contents($storeFile), true) : [];

$forUser = array_values(array_filter($queue, fn($m) => $m['to'] === $userId));
$remaining = array_values(array_filter($queue, fn($m) => $m['to'] !== $userId));

file_put_contents($storeFile, json_encode($remaining));

echo json_encode(['messages' => $forUser]);
