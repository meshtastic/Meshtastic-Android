/*
 * Copyright (c) 2025-2026 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.meshtastic.core.ui.icon

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.vectorResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.ic_add
import org.meshtastic.core.resources.ic_add_reaction
import org.meshtastic.core.resources.ic_clear
import org.meshtastic.core.resources.ic_close
import org.meshtastic.core.resources.ic_cloud_download
import org.meshtastic.core.resources.ic_cloud_upload
import org.meshtastic.core.resources.ic_content_copy
import org.meshtastic.core.resources.ic_delete
import org.meshtastic.core.resources.ic_done
import org.meshtastic.core.resources.ic_download
import org.meshtastic.core.resources.ic_drag_handle
import org.meshtastic.core.resources.ic_edit
import org.meshtastic.core.resources.ic_file_download
import org.meshtastic.core.resources.ic_filter_alt
import org.meshtastic.core.resources.ic_filter_alt_off
import org.meshtastic.core.resources.ic_folder
import org.meshtastic.core.resources.ic_folder_open
import org.meshtastic.core.resources.ic_mark_chat_read
import org.meshtastic.core.resources.ic_more_vert
import org.meshtastic.core.resources.ic_offline_share
import org.meshtastic.core.resources.ic_output
import org.meshtastic.core.resources.ic_play_arrow
import org.meshtastic.core.resources.ic_qr_code
import org.meshtastic.core.resources.ic_qr_code_2
import org.meshtastic.core.resources.ic_qr_code_scanner
import org.meshtastic.core.resources.ic_refresh
import org.meshtastic.core.resources.ic_reply
import org.meshtastic.core.resources.ic_save
import org.meshtastic.core.resources.ic_search
import org.meshtastic.core.resources.ic_select_all
import org.meshtastic.core.resources.ic_send
import org.meshtastic.core.resources.ic_share
import org.meshtastic.core.resources.ic_sort
import org.meshtastic.core.resources.ic_system_update
import org.meshtastic.core.resources.ic_thumb_up
import org.meshtastic.core.resources.ic_upload

val MeshtasticIcons.Add: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_add)
val MeshtasticIcons.AddReaction: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_add_reaction)
val MeshtasticIcons.Clear: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_clear)
val MeshtasticIcons.Close: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_close)
val MeshtasticIcons.Copy: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_content_copy)
val MeshtasticIcons.Delete: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_delete)
val MeshtasticIcons.Edit: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_edit)
val MeshtasticIcons.More: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_more_vert)
val MeshtasticIcons.Refresh: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_refresh)
val MeshtasticIcons.Reply: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_reply)
val MeshtasticIcons.Save: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_save)
val MeshtasticIcons.Search: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_search)
val MeshtasticIcons.Send: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_send)
val MeshtasticIcons.Share: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_share)
val MeshtasticIcons.Sort: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_sort)
val MeshtasticIcons.CloudDownload: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_cloud_download)
val MeshtasticIcons.Folder: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_folder)
val MeshtasticIcons.SystemUpdate: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_system_update)
val MeshtasticIcons.SelectAll: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_select_all)
val MeshtasticIcons.ThumbUp: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_thumb_up)
val MeshtasticIcons.MarkChatRead: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_mark_chat_read)
val MeshtasticIcons.QrCode2: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_qr_code_2)

val MeshtasticIcons.Download: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_download)
val MeshtasticIcons.Upload: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_upload)
val MeshtasticIcons.DragHandle: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_drag_handle)
val MeshtasticIcons.Done: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_done)
val MeshtasticIcons.QrCode: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_qr_code)
val MeshtasticIcons.FolderOpen: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_folder_open)
val MeshtasticIcons.Output: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_output)
val MeshtasticIcons.FileDownload: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_file_download)
val MeshtasticIcons.PlayArrow: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_play_arrow)
val MeshtasticIcons.FilterAlt: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_filter_alt)
val MeshtasticIcons.FilterAltOff: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_filter_alt_off)
val MeshtasticIcons.OfflineShare: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_offline_share)
val MeshtasticIcons.CloudUpload: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_cloud_upload)
val MeshtasticIcons.QrCodeScanner: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_qr_code_scanner)
