import { useEffect, useState, useCallback, useRef } from 'react';
import { Table, Button, Space, Tag, Drawer, Input, Switch, Form, message, Alert, Typography, Divider } from 'antd';
import { ReloadOutlined, PlusOutlined, DeleteOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import notificationApi from '../../api/notificationApi';
import type { NotificationTemplateDto, UpdateTemplateRequest } from '../../api/types';

const { Text } = Typography;

const typeColor: Record<string, string> = { SMS: 'blue', EMAIL: 'purple' };

function useTypeLabel(t: (key: string) => string): Record<string, string> {
  return {
    SMS: t('notifications.sms'),
    EMAIL: t('notifications.email'),
  };
}

interface VarEntry {
  key: string;
  value: string;
}

function parseVariables(json: string | undefined): VarEntry[] {
  if (!json) return [];
  try {
    const obj = JSON.parse(json);
    return Object.entries(obj).map(([k, v]) => ({ key: k, value: String(v) }));
  } catch {
    return [];
  }
}

function serializeVariables(entries: VarEntry[]): string {
  const obj: Record<string, string> = {};
  for (const e of entries) {
    if (e.key.trim()) obj[e.key.trim()] = e.value;
  }
  return JSON.stringify(obj);
}

function resolveTemplate(body: string, variables: VarEntry[]): string {
  let result = body;
  for (const v of variables) {
    result = result.split(`{{${v.key}}}`).join(v.value || `{{${v.key}}}`);
  }
  return result;
}

function NotificationsPage() {
  const { t } = useTranslation();
  const typeLabel = useTypeLabel(t);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [data, setData] = useState<NotificationTemplateDto[]>([]);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [selected, setSelected] = useState<NotificationTemplateDto | null>(null);
  const [variables, setVariables] = useState<VarEntry[]>([]);
  const [previewBody, setPreviewBody] = useState('');
  const [form] = Form.useForm();
  const bodyRef = useRef<HTMLTextAreaElement>(null);

  const fetchData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await notificationApi.listTemplates();
      setData(res.data?.data || []);
    } catch {
      setError(t('notifications.loadFailed'));
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => { fetchData(); }, [fetchData]);

  const insertVariable = (name: string) => {
    const el = bodyRef.current;
    if (!el) return;
    const start = el.selectionStart;
    const end = el.selectionEnd;
    const body = form.getFieldValue('body') || '';
    const tag = `{{${name}}}`;
    const newBody = body.slice(0, start) + tag + body.slice(end);
    form.setFieldsValue({ body: newBody });
    updatePreview(newBody, variables);
    setTimeout(() => {
      el.focus();
      el.setSelectionRange(start + tag.length, start + tag.length);
    }, 0);
  };

  const updatePreview = (body: string, vars: VarEntry[]) => {
    setPreviewBody(resolveTemplate(body, vars));
  };

  const openDrawer = (record: NotificationTemplateDto) => {
    setSelected(record);
    const vars = parseVariables(record.variablesJson);
    setVariables(vars);
    updatePreview(record.body, vars);
    form.setFieldsValue({
      subject: record.subject,
      body: record.body,
      description: record.description,
      isActive: record.isActive,
    });
    setDrawerOpen(true);
  };

  const addVariable = () => {
    setVariables([...variables, { key: '', value: '' }]);
  };

  const removeVariable = (index: number) => {
    setVariables(variables.filter((_, i) => i !== index));
  };

  const updateVariable = (index: number, field: 'key' | 'value', val: string) => {
    const next = variables.map((v, i) => i === index ? { ...v, [field]: val } : v);
    setVariables(next);
    updatePreview(form.getFieldValue('body') || '', next);
  };

  const handleSave = async () => {
    if (!selected) return;
    try {
      const values = await form.validateFields();
      const req: UpdateTemplateRequest = {
        subject: values.subject,
        body: values.body,
        variablesJson: serializeVariables(variables),
        description: values.description,
        isActive: values.isActive,
      };
      await notificationApi.updateTemplate(selected.id, req);
      message.success(t('notifications.updateSuccess'));
      setDrawerOpen(false);
      fetchData();
    } catch {
      // validation error
    }
  };

  const columns = [
    {
      title: t('notifications.templateName'), dataIndex: 'templateKey', width: 160, ellipsis: true,
    },
    {
      title: t('notifications.type'), dataIndex: 'type', width: 80,
      render: (tp: string) => <Tag color={typeColor[tp] || 'default'}>{typeLabel[tp] || tp}</Tag>,
    },
    { title: t('notifications.subject'), dataIndex: 'subject', ellipsis: true },
    {
      title: t('notifications.enabled'), dataIndex: 'isActive', width: 60,
      render: (v: boolean) => v ? <Tag color="green">{t('common.yes')}</Tag> : <Tag>{t('common.no')}</Tag>,
    },
  ];

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Button icon={<ReloadOutlined />} onClick={fetchData}>{t('common.refresh')}</Button>
      </Space>
      {error && <Alert type="error" message={error} action={<Button onClick={fetchData}>{t('common.retry')}</Button>} style={{ marginBottom: 16 }} />}
      <Table columns={columns} dataSource={data} rowKey="id"
        loading={loading} pagination={false}
        onRow={(record) => ({
          onClick: () => openDrawer(record),
          style: { cursor: 'pointer' },
        })} />
      <Drawer title={`${t('notifications.editTemplate')}: ${selected?.templateKey || ''}`} open={drawerOpen}
        onClose={() => setDrawerOpen(false)} width={640}
        extra={<Button type="primary" onClick={handleSave}>{t('common.save')}</Button>}>
        <Form form={form} layout="vertical">
          <Form.Item name="subject" label={t('notifications.subject')} rules={[{ required: true }]}>
            <Input />
          </Form.Item>

          {/* ── Variables ── */}
          <div style={{ marginBottom: 16 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
              <Text strong>{t('notifications.variables')}</Text>
              <Button size="small" icon={<PlusOutlined />} onClick={addVariable}>{t('notifications.addVariable')}</Button>
            </div>
            {variables.length === 0 && (
              <Text type="secondary" style={{ fontSize: 12 }}>{t('notifications.noVariables')}</Text>
            )}
            {variables.map((v, i) => (
              <Space key={i} style={{ display: 'flex', marginBottom: 6 }} align="start">
                <Input
                  placeholder={t('notifications.varName')}
                  value={v.key}
                  onChange={(e) => updateVariable(i, 'key', e.target.value)}
                  style={{ width: 140 }}
                  size="small"
                />
                <Input
                  placeholder={t('notifications.varValue')}
                  value={v.value}
                  onChange={(e) => updateVariable(i, 'value', e.target.value)}
                  style={{ width: 200 }}
                  size="small"
                />
                <Button size="small" danger icon={<DeleteOutlined />} onClick={() => removeVariable(i)} />
              </Space>
            ))}
          </div>

          {/* ── Body with variable chips ── */}
          <Form.Item name="body" label={t('notifications.body')} rules={[{ required: true }]}>
            <>
              {variables.filter((v) => v.key.trim()).length > 0 && (
                <div style={{ marginBottom: 8 }}>
                  {variables.filter((v) => v.key.trim()).map((v) => (
                    <Tag key={v.key}
                      color="blue"
                      style={{ cursor: 'pointer', marginBottom: 4 }}
                      onClick={() => insertVariable(v.key.trim())}
                    >{`{{${v.key.trim()}}}`}</Tag>
                  ))}
                  <Text type="secondary" style={{ fontSize: 11, marginLeft: 8 }}>{t('notifications.clickToInsert')}</Text>
                </div>
              )}
              <Input.TextArea
                ref={bodyRef as never}
                rows={8}
                placeholder={t('notifications.variablePlaceholder')}
                onChange={(e) => updatePreview(e.target.value, variables)}
              />
            </>
          </Form.Item>

          {/* ── Preview ── */}
          {previewBody && (
            <>
              <Divider style={{ margin: '8px 0' }} />
              <Text strong>{t('notifications.preview')}</Text>
              <div style={{
                marginTop: 8, padding: 12, background: 'var(--cosmic-elevated)',
                borderRadius: 6, whiteSpace: 'pre-wrap', fontSize: 13,
                lineHeight: 1.6, border: '1px solid var(--cosmic-border)',
              }}>
                {previewBody}
              </div>
            </>
          )}

          <Form.Item name="description" label={t('notifications.description')} style={{ marginTop: 16 }}>
            <Input />
          </Form.Item>
          <Form.Item name="isActive" label={t('notifications.enabled')} valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Drawer>
    </div>
  );
}

export default NotificationsPage;
