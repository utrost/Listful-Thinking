import { describe, expect, it } from 'vitest';
import { itemFormFieldsForListType, listFormRulesForType } from './listTypes';

describe('list type UI rules', () => {
  it('requires an event target date only for event lists', () => {
    expect(listFormRulesForType('EVENT')).toEqual({ showTargetDate: true, requireTargetDate: true });
    expect(listFormRulesForType('WISH')).toEqual({ showTargetDate: false, requireTargetDate: false });
    expect(listFormRulesForType('CHORE')).toEqual({ showTargetDate: false, requireTargetDate: false });
    expect(listFormRulesForType('TODO')).toEqual({ showTargetDate: false, requireTargetDate: false });
    expect(listFormRulesForType('GROCERY')).toEqual({ showTargetDate: false, requireTargetDate: false });
  });

  it('shows item fields according to list type', () => {
    expect(itemFormFieldsForListType('WISH')).toEqual({
      showUrl: true,
      showImageUrl: true,
      showPrice: true,
      showDueDate: false,
      showRecurrenceRule: false
    });
    expect(itemFormFieldsForListType('CHORE')).toEqual({
      showUrl: false,
      showImageUrl: false,
      showPrice: false,
      showDueDate: true,
      showRecurrenceRule: true,
      showResponsibility: true
    });
    expect(itemFormFieldsForListType('EVENT')).toEqual({
      showUrl: false,
      showImageUrl: false,
      showPrice: false,
      showDueDate: true,
      showRecurrenceRule: false,
      showResponsibility: true
    });
    expect(itemFormFieldsForListType('TODO')).toEqual({
      showUrl: false,
      showImageUrl: false,
      showPrice: false,
      showDueDate: true,
      showRecurrenceRule: false,
      showResponsibility: true
    });
    expect(itemFormFieldsForListType('GROCERY')).toEqual({
      showUrl: false,
      showImageUrl: false,
      showPrice: false,
      showDueDate: false,
      showRecurrenceRule: false,
      showQuantity: true,
      showCategory: true,
      showResponsibility: true
    });
  });
});
