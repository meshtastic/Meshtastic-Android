
import os
import re

files = [
    "core/data/src/commonMain/kotlin/org/meshtastic/core/data/manager/CommandSenderImpl.kt",
    "core/data/src/commonMain/kotlin/org/meshtastic/core/data/manager/MeshActionHandlerImpl.kt",
    "core/data/src/commonMain/kotlin/org/meshtastic/core/data/manager/MeshConfigFlowManagerImpl.kt",
    "core/data/src/commonMain/kotlin/org/meshtastic/core/data/manager/MeshConfigHandlerImpl.kt",
    "core/data/src/commonMain/kotlin/org/meshtastic/core/data/manager/MeshConnectionManagerImpl.kt",
    "core/data/src/commonMain/kotlin/org/meshtastic/core/data/manager/MeshDataHandlerImpl.kt",
    "core/data/src/commonMain/kotlin/org/meshtastic/core/data/manager/MeshMessageProcessorImpl.kt",
    "core/data/src/commonMain/kotlin/org/meshtastic/core/data/manager/MqttManagerImpl.kt",
    "core/data/src/commonMain/kotlin/org/meshtastic/core/data/manager/NeighborInfoHandlerImpl.kt",
    "core/data/src/commonMain/kotlin/org/meshtastic/core/data/manager/PacketHandlerImpl.kt",
    "core/data/src/commonMain/kotlin/org/meshtastic/core/data/manager/TracerouteHandlerImpl.kt",
    "feature/connections/src/commonMain/kotlin/org/meshtastic/feature/connections/ScannerViewModel.kt",
    "feature/map/src/commonMain/kotlin/org/meshtastic/feature/map/BaseMapViewModel.kt",
    "feature/messaging/src/commonMain/kotlin/org/meshtastic/feature/messaging/MessageViewModel.kt",
    "feature/messaging/src/commonMain/kotlin/org/meshtastic/feature/messaging/QuickChatViewModel.kt",
    "feature/messaging/src/commonMain/kotlin/org/meshtastic/feature/messaging/ui/contact/ContactsViewModel.kt",
    "feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/detail/CommonNodeRequestActions.kt",
    "feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/detail/NodeManagementActions.kt",
    "feature/settings/src/commonMain/kotlin/org/meshtastic/feature/settings/debugging/DebugViewModel.kt"
]

for file_path in files:
    if not os.path.exists(file_path):
        print(f"File not found: {file_path}")
        continue
    
    with open(file_path, 'r') as f:
        content = f.read()
    
    # Replace Dispatchers.IO with ioDispatcher
    new_content = re.sub(r'\bDispatchers\.IO\b', 'ioDispatcher', content)
    new_content = re.sub(r'\bkotlinx\.coroutines\.Dispatchers\.IO\b', 'ioDispatcher', new_content)
    
    if new_content == content:
        print(f"No changes in {file_path}")
        continue

    # Add import if missing
    if 'import org.meshtastic.core.common.util.ioDispatcher' not in new_content:
        # Find the package line to insert after, or just after other imports
        lines = new_content.splitlines()
        inserted = False
        for i, line in enumerate(lines):
            if line.startswith('package '):
                lines.insert(i + 2, 'import org.meshtastic.core.common.util.ioDispatcher')
                inserted = True
                break
        if not inserted:
            lines.insert(0, 'import org.meshtastic.core.common.util.ioDispatcher')
        new_content = '\n'.join(lines) + '\n'

    # Check if Dispatchers is still used
    if 'Dispatchers.' not in new_content:
        new_content = re.sub(r'^import kotlinx\.coroutines\.Dispatchers\n', '', new_content, flags=re.MULTILINE)

    with open(file_path, 'w') as f:
        f.write(new_content)
    print(f"Fixed {file_path}")
