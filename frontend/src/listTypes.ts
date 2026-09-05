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
  showQuantity?: boolean;
  showCategory?: boolean;
  showResponsibility?: boolean;
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
    showDueDate: type === 'TODO' || type === 'CHORE' || type === 'EVENT',
    showRecurrenceRule: type === 'CHORE',
    ...(type === 'TODO' || type === 'GROCERY' || type === 'CHORE' || type === 'EVENT' ? { showResponsibility: true } : {}),
    ...(type === 'GROCERY' ? { showQuantity: true, showCategory: true } : {})
  };
}
