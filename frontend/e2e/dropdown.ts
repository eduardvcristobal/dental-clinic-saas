import { expect, type Locator, type Page } from '@playwright/test'

type Scope = Page | Locator

/**
 * Opens a ws-dropdown by its accessible name and picks one option.
 * The dropdown replaces native <select> elements, so options are chosen through the listbox.
 */
export async function pickDropdown(scope: Scope, name: string, option: string | RegExp) {
  const trigger = scope.getByRole('combobox', { name, exact: true })
  await trigger.click()
  const listbox = scope.getByRole('listbox', { name, exact: true })
  await expect(listbox).toBeVisible()
  await listbox.getByRole('option', { name: option, exact: typeof option === 'string' }).click()
  await expect(listbox).toHaveCount(0)
}

/** Reads the text currently shown on a ws-dropdown trigger. */
export async function dropdownText(scope: Scope, name: string) {
  return (await scope.getByRole('combobox', { name, exact: true }).textContent())?.trim() ?? ''
}
