export type DropdownOption = { value: string; label: string; hint?: string; disabled?: boolean }

/** Index of the option matching the current value, or -1 when nothing is selected. */
export function selectedIndex(options: DropdownOption[], value: string): number {
  return options.findIndex(o => o.value === value)
}

/**
 * Next active option index for arrow-key navigation.
 * Wraps around the list; a negative `current` starts at the first or last option.
 */
export function moveActiveIndex(current: number, length: number, delta: number): number {
  if (length <= 0) return -1
  if (current < 0) return delta >= 0 ? 0 : length - 1
  return (current + delta + length) % length
}

/** First selectable option at or after `from`, wrapping once. -1 when every option is disabled. */
export function nextEnabledIndex(options: DropdownOption[], from: number, delta: number): number {
  if (options.length === 0 || delta === 0) return -1
  let index = from
  for (let step = 0; step < options.length; step++) {
    index = index < 0 ? (delta > 0 ? 0 : options.length - 1) : moveActiveIndex(index, options.length, delta)
    if (index >= 0 && !options[index].disabled) return index
  }
  return -1
}

/** Placeholder shown on the trigger when the current value has no matching option. */
export function triggerText(options: DropdownOption[], value: string, placeholder: string): string {
  const match = options.find(o => o.value === value)
  return match ? match.label : placeholder
}
