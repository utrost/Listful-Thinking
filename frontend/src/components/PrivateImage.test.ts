// @vitest-environment happy-dom
import { mount } from '@vue/test-utils';
import { describe, it, expect } from 'vitest';
import PrivateImage from './PrivateImage.vue';
import { i18n } from '../i18n';

describe('Private images', () => {
  it('contacts an external image only after consent for that exact URL', async () => {
    const wrapper = mount(PrivateImage, { props: { src: 'https://images.example.test/private.png', alt: 'Private photo' }, global: { plugins: [i18n] } });
    expect(wrapper.find('img').exists()).toBe(false);
    await wrapper.get('button').trigger('click');
    expect(wrapper.get('img').attributes('src')).toBe('https://images.example.test/private.png');
    expect(wrapper.get('img').attributes('referrerpolicy')).toBe('no-referrer');
    await wrapper.setProps({ src: 'https://images.example.test/different.png' });
    expect(wrapper.find('img').exists()).toBe(false);
    wrapper.unmount();
  });
  it('renders uploaded raster images without contacting another host', () => {
    const wrapper = mount(PrivateImage, { props: { src: 'data:image/png;base64,aGVsbG8=', alt: 'Uploaded photo' }, global: { plugins: [i18n] } });
    expect(wrapper.find('img').exists()).toBe(true);
    expect(wrapper.find('button').exists()).toBe(false);
    wrapper.unmount();
  });
  it('never renders active content or URLs with embedded credentials as images', () => {
    for (const src of ['javascript:alert(1)', 'data:image/svg+xml,<svg onload="alert(1)"/>', 'https://user:password@images.example.test/photo']) {
      const wrapper = mount(PrivateImage, { props: { src, alt: 'Invalid' }, global: { plugins: [i18n] } });
      expect(wrapper.find('img').exists()).toBe(false);
      expect(wrapper.find('button').exists()).toBe(false);
      wrapper.unmount();
    }
  });
});
