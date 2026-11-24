@Composable
private fun CustomCarAppsSetting() {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(getSelectedCustomCarApps(context)) }
    val apps = remember(context) { loadLaunchablePhoneApps(context) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Custom Car Apps",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Surface(
            onClick = { expanded = !expanded },
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (selected.isEmpty()) "Select apps to show & run on car screen"
                        else "${selected.size} app(s) enabled for car screen",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        if (expanded) "Tap to collapse app list" else "Tap to choose any installed app",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    if (expanded) "Hide" else "Choose",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        if (expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                apps.forEach { appInfo ->
                    val checked = appInfo.packageName in selected
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    appInfo.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    appInfo.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            GsButton(
                                onClick = { launchCustomCarApp(context, appInfo.packageName) },
                                tone = GsTone.Tonal,
                                modifier = Modifier.height(40.dp),
                            ) {
                                Text("Run", style = MaterialTheme.typography.labelLarge)
                            }
                            Spacer(Modifier.width(10.dp))
                            GsSwitch(
                                checked = checked,
                                onCheckedChange = { isOn ->
                                    val updated = if (isOn) selected + appInfo.packageName else selected - appInfo.packageName
                                    selected = updated
                                    setSelectedCustomCarApps(context, updated)
                                    warmLauncherApps(context)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}