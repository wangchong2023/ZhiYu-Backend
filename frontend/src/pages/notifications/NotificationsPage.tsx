import { useEffect, useState, useCallback } from 'react';
import { Table, Button, Space, Tag, Drawer, Input, Switch, Form, message, Alert } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import notificationApi from '../../api/notificationApi';
import type { NotificationTemplateDto, UpdateTemplateRequest } from '../../api/types';

const typeColor: Record<string, string> = { SMS: 'blue', EMAIL: 'purple' };

function NotificationsPage() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [data, setData] = useState<NotificationTemplateDto[]>([]);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [selected, setSelected] = useState<NotificationTemplateDto | null>(null);
  const [form] = Form.useForm();

  const fetchData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await notificationApi.listTemplates();
      setData(res.data?.data || []);
    } catch {
      setError('加载通知模板失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchData(); }, [fetchData]);

  const openDrawer = (record: NotificationTemplateDto) => {
    setSelected(record);
    form.setFieldsValue({
      subject: record.subject,
      body: record.body,
      variablesJson: record.variablesJson,
      description: record.description,
      isActive: record.isActive,
    });
    setDrawerOpen(true);
  };

  const handleSave = async () => {
    if (!selected) return;
    try {
      const values = await form.validateFields();
      const req: UpdateTemplateRequest = {
        subject: values.subject,
        body: values.body,
        variablesJson: values.variablesJson,
        description: values.description,
        isActive: values.isActive,
      };
      await notificationApi.updateTemplate(selected.id, req);
      message.success('模板已更新');
      setDrawerOpen(false);
      fetchData();
    } catch {
      // validation error
    }
  };

  const columns = [
    {
      title: '模板Key', dataIndex: 'templateKey', width: 160, ellipsis: true,
    },
    {
      title: '类型', dataIndex: 'type', width: 80,
      render: (t: string) => <Tag color={typeColor[t] || 'default'}>{t}</Tag>,
    },
    { title: '主题', dataIndex: 'subject', ellipsis: true },
    {
      title: '启用', dataIndex: 'isActive', width: 60,
      render: (v: boolean) => v ? <Tag color="green">是</Tag> : <Tag>否</Tag>,
    },
    {
      title: '描述', dataIndex: 'description', width: 120, ellipsis: true,
      render: (v: string) => v || '-',
    },
  ];

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Button icon={<ReloadOutlined />} onClick={fetchData}>刷新</Button>
      </Space>
      {error && <Alert type="error" message={error} action={<Button onClick={fetchData}>重试</Button>} style={{ marginBottom: 16 }} />}
      <Table columns={columns} dataSource={data} rowKey="id"
        loading={loading} pagination={false}
        onRow={(record) => ({
          onClick: () => openDrawer(record),
          style: { cursor: 'pointer' },
        })} />
      <Drawer title={`编辑模板: ${selected?.templateKey || ''}`} open={drawerOpen}
        onClose={() => setDrawerOpen(false)} width={560}
        extra={<Button type="primary" onClick={handleSave}>保存</Button>}>
        <Form form={form} layout="vertical">
          <Form.Item name="subject" label="主题" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="body" label="正文" rules={[{ required: true }]}>
            <Input.TextArea rows={8} placeholder="支持 {{variable}} 变量替换" />
          </Form.Item>
          <Form.Item name="variablesJson" label="变量定义 (JSON)">
            <Input.TextArea rows={3} placeholder='{"code": "验证码"}' />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input />
          </Form.Item>
          <Form.Item name="isActive" label="启用" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Drawer>
    </div>
  );
}

export default NotificationsPage;
