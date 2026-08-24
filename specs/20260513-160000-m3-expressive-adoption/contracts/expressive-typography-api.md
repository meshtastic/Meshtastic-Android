# Contract: Expressive Typography API

**Module**: `core/ui` | **Source Set**: `commonMain`

## Public API

### Theme Typography Access

```kotlin
// Standard access (unchanged)
MaterialTheme.typography.bodyLarge
MaterialTheme.typography.titleMedium

// Expressive emphasized access (requires @OptIn)
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
MaterialTheme.typography.displayLargeEmphasized
MaterialTheme.typography.headlineMediumEmphasized
MaterialTheme.typography.titleMediumEmphasized
MaterialTheme.typography.bodyLargeEmphasized
MaterialTheme.typography.labelLargeEmphasized
```

### Typography Definition (Type.kt)

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val AppTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,      // Design standards §5 minimum
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,      // Design standards §5 minimum
        lineHeight = 22.sp,
        letterSpacing = 0.25.sp,
    ),
    // All other slots use M3 defaults — MaterialExpressiveTheme
    // auto-generates emphasized variants from these base styles
)
```

## Usage Guidelines

| UI Context | Typography Slot | When Emphasized |
|------------|----------------|-----------------|
| Screen titles | `titleLarge` | Always use emphasized variant |
| Section headers | `titleMedium` | Use emphasized variant |
| Node names in list | `bodyLarge` | Use emphasized for own node |
| Metric values (signal, battery) | `headlineSmall` | Primary metric value |
| Metric labels | `bodyMedium` | Never emphasized |
| Message body text | `bodyLarge` | Never emphasized |
| Timestamps, metadata | `labelMedium` | Never emphasized |
| Navigation labels | `labelLarge` | Active destination uses emphasized |
| Button text | `labelLarge` | Always emphasized |
| Dialog titles | `headlineSmall` | Always use emphasized variant |

## Behavioral Contracts

| Behavior | Specification |
|----------|--------------|
| Minimum body size | `bodyMedium.fontSize >= 16.sp` (hard requirement from design standards) |
| Dynamic Type scaling | All sizes must scale with system `fontScale` up to 200% without clipping |
| Pre-Android 12 | Graceful degradation: standard weight variants (400/500/700) instead of variable font intermediate weights |
| Android 12+ | System Roboto Flex provides full variable weight expression |
| Desktop/iOS | System default font family; same `sp` sizes; weight expression depends on platform font |

## Migration Checklist (for each screen)

1. Replace hardcoded `TextStyle` with `MaterialTheme.typography.*` reference
2. Identify heading elements → use emphasized variant
3. Verify no `fontSize` below 16.sp for body text
4. Run screenshot test to capture new visual
5. Verify TalkBack still reads correct content (no regression)
