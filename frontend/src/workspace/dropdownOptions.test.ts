import { describe, expect, it } from 'vitest'
import { moveActiveIndex, nextEnabledIndex, selectedIndex, triggerText } from './dropdownOptions'
import type { DropdownOption } from './dropdownOptions'

const options: DropdownOption[] = [
  { value: 'd1', label: 'Dr. Ada' },
  { value: 'd2', label: 'Dr. Abel', disabled: true },
  { value: 'd3', label: 'Dr. Ada Lovelace' },
]

describe('selectedIndex', () => {
  it('finds the option matching the value', () => {
    expect(selectedIndex(options, 'd3')).toBe(2)
  })
  it('returns -1 when nothing is selected', () => {
    expect(selectedIndex(options, '')).toBe(-1)
  })
})

describe('moveActiveIndex', () => {
  it('moves forward and wraps around', () => {
    expect(moveActiveIndex(0, 3, 1)).toBe(1)
    expect(moveActiveIndex(2, 3, 1)).toBe(0)
  })
  it('moves backward and wraps around', () => {
    expect(moveActiveIndex(2, 3, -1)).toBe(1)
    expect(moveActiveIndex(0, 3, -1)).toBe(2)
  })
  it('starts at the first or last option when nothing is active', () => {
    expect(moveActiveIndex(-1, 3, 1)).toBe(0)
    expect(moveActiveIndex(-1, 3, -1)).toBe(2)
  })
  it('returns -1 for an empty list', () => {
    expect(moveActiveIndex(-1, 0, 1)).toBe(-1)
  })
})

describe('nextEnabledIndex', () => {
  it('skips disabled options when moving forward', () => {
    expect(nextEnabledIndex(options, 0, 1)).toBe(2)
  })
  it('skips disabled options when moving backward', () => {
    expect(nextEnabledIndex(options, 2, -1)).toBe(0)
  })
  it('starts at the first enabled option when nothing is active', () => {
    expect(nextEnabledIndex(options, -1, 1)).toBe(0)
  })
  it('jumps to the last option for End', () => {
    expect(nextEnabledIndex(options, options.length, -1)).toBe(2)
  })
  it('returns -1 when every option is disabled', () => {
    const allDisabled: DropdownOption[] = [{ value: 'a', label: 'A', disabled: true }, { value: 'b', label: 'B', disabled: true }]
    expect(nextEnabledIndex(allDisabled, -1, 1)).toBe(-1)
  })
  it('returns -1 for an empty list', () => {
    expect(nextEnabledIndex([], -1, 1)).toBe(-1)
  })
})

describe('triggerText', () => {
  it('shows the label of the selected option', () => {
    expect(triggerText(options, 'd3', 'Select dentist')).toBe('Dr. Ada Lovelace')
  })
  it('falls back to the placeholder when nothing is selected', () => {
    expect(triggerText(options, '', 'Select dentist')).toBe('Select dentist')
  })
  it('falls back to the placeholder when the value no longer exists', () => {
    expect(triggerText(options, 'gone', 'Select dentist')).toBe('Select dentist')
  })
})
