import {describe,expect,it} from 'vitest'
import {deliveryStatusLabel} from './notificationStatus'

describe('M8.10.23 notification status rendering',()=>{
 it('never labels provider acceptance as patient delivery',()=>{
  expect(deliveryStatusLabel('ACCEPTED')).toContain('delivery unconfirmed')
  expect(deliveryStatusLabel('CLAIMED')).toContain('unconfirmed')
  expect(deliveryStatusLabel('DELIVERED')).toContain('provider reported')
 })
 it('uses fail-closed labels for queued, suppressed and unknown states',()=>{
  expect(deliveryStatusLabel('PENDING')).toContain('not sent')
  expect(deliveryStatusLabel('SUPPRESSED')).toBe('Not sending')
  expect(deliveryStatusLabel('UNRECOGNIZED')).toBe('Delivery status unknown')
 })
})
