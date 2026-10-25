with open('app/src/main/java/com/music/bitchord/MainActivity.kt', 'r', encoding='utf-8') as f:
    lines = f.readlines()

new_lines = []
i = 0
n = len(lines)
while i < n:
    line = lines[i]
    if line.startswith('<<<<<<< HEAD'):
        marker_head = []
        i += 1
        while i < n and not lines[i].startswith('======='):
            marker_head.append(lines[i])
            i += 1
        i += 1 # skip =======
        marker_v181 = []
        while i < n and not lines[i].startswith('>>>>>>>'):
            marker_v181.append(lines[i])
            i += 1
        i += 1 # skip >>>>>>>
        
        head_text = ''.join(marker_head)
        v181_text = ''.join(marker_v181)
        
        if 'showGlobalDjSheet' in head_text:
            # Marker 1
            new_lines.extend(marker_v181)
            new_lines.append('    var showGlobalDjSheet by remember { mutableStateOf(false) }\n')
            print('Resolved marker 1')
        elif 'key == "replay"' in head_text or 'ReplayScreen' in head_text:
            # Marker 2
            new_lines.extend(marker_v181)
            print('Resolved marker 2')
        elif 'isDjActiveTop' in head_text:
            # Marker 3
            # v181 has `if (!(route != null && AppRoute.Settings in navigator)) {`
            new_lines.extend(marker_v181)
            # Add DJ button inside that block
            dj_code = """                            val isDjActiveTop by AiDjManager.isActive.collectAsStateWithLifecycle()
                            IconButton(
                                onClick = { showGlobalDjSheet = true },
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                colors = if (isDjActiveTop) listOf(Color(0xFF1DB954), Color(0xFF00D2FF))
                                                else listOf(Color(0xFF1DB954).copy(alpha = 0.35f), Color(0xFF00D2FF).copy(alpha = 0.35f)),
                                            ),
                                        ),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.GraphicEq,
                                        contentDescription = stringResource(R.string.ai_dj_title),
                                        tint = if (isDjActiveTop) Color.White else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
"""
            new_lines.append(dj_code)
            print('Resolved marker 3')
        elif 'isLocalPlaylist' in head_text and 'inCache' in v181_text:
            # Marker 4
            new_lines.append('                ?.takeIf { !fromPlayer && (song.setVideoId != null || isLocalPlaylist) }\n')
            new_lines.extend(marker_v181[1:])
            print('Resolved marker 4')
        elif 'addingSongsToPlaylist' in head_text:
            # Marker 5
            adding_idx = 0
            for idx, hl in enumerate(marker_head):
                if 'addingSongsToPlaylist?.let' in hl:
                    adding_idx = idx
                    break
            new_lines.extend(marker_head[adding_idx:])
            print('Resolved marker 5')
        else:
            print('Unknown marker!', head_text[:100])
    else:
        new_lines.append(line)
        i += 1

with open('app/src/main/java/com/music/bitchord/MainActivity.kt', 'w', encoding='utf-8') as f:
    f.writelines(new_lines)
print('Done resolving MainActivity.kt')
