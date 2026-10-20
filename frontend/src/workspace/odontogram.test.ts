import { describe,expect,it } from 'vitest'
import { teethFor,surfaceOptions } from './Odontogram'

describe('dentition selection',()=>{
 it('includes 32 permanent FDI identifiers',()=>{
  const values=teethFor('permanent')
  expect(values).toHaveLength(32)
  expect(values).toContain(11)
  expect(values).toContain(48)
  expect(values).not.toContain(19)
 })
 it('includes 20 primary identifiers',()=>{
  const values=teethFor('primary')
  expect(values).toHaveLength(20)
  expect(values).toContain(51)
  expect(values).toContain(85)
  expect(values).not.toContain(56)
 })
 it('shows all 52 standard teeth in mixed dentition mode',()=>{
  const values=teethFor('mixed')
  expect(values).toHaveLength(52)
  expect(new Set(values).size).toBe(52)
 })\n it('supports appropriate incisal and occlusal choices',()=>{
  expect(surfaceOptions(11)).toContain('I')
  expect(surfaceOptions(11)).not.toContain('O')
  expect(surfaceOptions(16)).toContain('O')
  expect(surfaceOptions(16)).not.toContain('I')
  expect(surfaceOptions(55)).toContain('O')
  expect(surfaceOptions(26)).toContain('P')
  expect(surfaceOptions(46)).not.toContain('P')
 })
})
