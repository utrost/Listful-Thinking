import type { ListType } from './api/client';

export type ListFormRules = {
  showTargetDate: boolean;
  requireTargetDate: boolean;
};

export type ItemFormFields = {
  showUrl: boolean;
  showImageUrl: boolean;
  showPrice: boolean;
  showDueDate: boolean;
  showRecurrenceRule: boolean;
};

export function listFormRulesForType(type: ListType): ListFormRules {
  return {
    showTargetDate: type === 'EVENT',
    requireTargetDate: type === 'EVENT'
  };
}

export function itemFormFieldsForListType(type: ListType): ItemFormFields {
  return {
    showUrl: type === 'WISH',
    showImageUrl: type === 'WISH',
    showPrice: type === 'WISH',
    showDueDate: type === 'CHORE' || type === 'EVENT',
    showRecurrenceRule: type === 'CHORE'
  };
}
